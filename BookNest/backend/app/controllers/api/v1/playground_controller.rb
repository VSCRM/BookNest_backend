require "open3"
require "timeout"
require "tmpdir"

module Api
  module V1
    # == Api::V1::PlaygroundController ========================================
    # Runs the Ruby snippets typed into frontend-playground (BookNest labs
    # 5-7: strings/arithmetic, arrays, classes/OOP) and lints them with
    # RuboCop. This is the real "Ruby backend" the playground talks to —
    # it did not exist before (the old /rubyback prefix was an unrelated
    # legacy HTML catalog site and has been removed).
    #
    # Public on purpose (no login required, same as the playground UI
    # itself), but every request runs the submitted code in its own
    # subprocess/process-group with wall-clock, CPU-time and memory limits
    # so one bad/malicious snippet can't hang or take down the server.
    # This is process-level sandboxing, not a full container jail — fine
    # for a teaching tool, but a public multi-tenant deployment should run
    # this behind an actual container/VM sandbox instead.
    class PlaygroundController < ApplicationController
      skip_before_action :verify_authenticity_token, raise: false

      MAX_SOURCE_BYTES = 20_000
      MAX_STDIN_BYTES  = 4_000
      MAX_OUTPUT_BYTES = 100_000
      WALL_TIMEOUT     = 5   # seconds, enforced from the parent process
      CPU_LIMIT        = 3   # seconds of CPU time, enforced via rlimit
      MEMORY_LIMIT     = 256 * 1024 * 1024 # bytes, enforced via rlimit

      # POST /api/v1/playground/execute
      # Body: { source: string, stdin: string }
      def execute
        source = params[:source].to_s
        stdin  = params[:stdin].to_s

        if source.bytesize > MAX_SOURCE_BYTES || stdin.bytesize > MAX_STDIN_BYTES
          return render json: {
            success: false, stdout: "", stderr: "Код або stdin завеликі", duration_ms: 0,
          }
        end

        render json: run_ruby(source, stdin)
      end

      # POST /api/v1/playground/lint
      # Body: { source: string }
      def lint
        source = params[:source].to_s

        if source.bytesize > MAX_SOURCE_BYTES
          return render json: { success: false, offenses: [], error: "Код завеликий" }
        end

        render json: run_rubocop(source)
      end

      private

      def run_ruby(source, stdin)
        Dir.mktmpdir("playground-") do |dir|
          script_path = File.join(dir, "main.rb")
          File.write(script_path, source)

          start = Process.clock_gettime(Process::CLOCK_MONOTONIC)
          out, err, ok = execute_sandboxed(script_path, stdin, dir)
          duration_ms = ((Process.clock_gettime(Process::CLOCK_MONOTONIC) - start) * 1000).round

          { success: ok, stdout: truncate(out), stderr: truncate(err), duration_ms: duration_ms }
        end
      rescue StandardError
        { success: false, stdout: "", stderr: "Внутрішня помилка сервера", duration_ms: 0 }
      end

      # Spawns `ruby main.rb` in its own process group with CPU/memory
      # rlimits, no inherited env vars, and a hard wall-clock timeout that
      # kills the whole group (so a snippet that forks can't escape it).
      def execute_sandboxed(script_path, stdin, dir)
        stdin_r,  stdin_w  = IO.pipe
        stdout_r, stdout_w = IO.pipe
        stderr_r, stderr_w = IO.pipe

        # `unsetenv_all: true` is the documented way to spawn with a blank
        # environment, but Process.spawn(env_hash, ...) actually *merges*
        # env_hash into the current ENV rather than replacing it — so
        # without this, the sandboxed script would inherit JWT_SECRET,
        # RAILS_MASTER_KEY, DB credentials, etc. straight from ENV, and
        # `puts ENV.to_h` in a submitted snippet would leak every secret
        # this process holds. Explicitly nil-ing out every current key
        # (nil deletes that var for the child, per Process.spawn's docs)
        # and only re-adding PATH/HOME achieves the same result reliably.
        sandbox_env = ENV.to_h.transform_values { nil }.merge("HOME" => dir, "PATH" => ENV["PATH"])

        pid = Process.spawn(
          sandbox_env,
          # `-Eutf-8:utf-8` sets Ruby's default external:internal encoding
          # explicitly, independent of the environment's locale. Without
          # it, wiping ENV above (LANG/LC_ALL included) makes Ruby fall
          # back to `US-ASCII` as the default external encoding, so
          # anything read via `gets` gets tagged US-ASCII even when it's
          # actually UTF-8 bytes (e.g. a Cyrillic name) — then concatenating
          # it with a UTF-8 source literal (`"Привіт, " + name`) raises
          # `Encoding::CompatibilityError`. This flag fixes that regardless
          # of what locale (if any) the host/container has configured.
          RbConfig.ruby, "-Eutf-8:utf-8", script_path,
          in: stdin_r, out: stdout_w, err: stderr_w,
          chdir: dir,
          pgroup: true,
          rlimit_cpu: CPU_LIMIT,
          rlimit_as: MEMORY_LIMIT
        )
        [stdin_r, stdout_w, stderr_w].each(&:close)

        stdin_w.write(stdin)
        stdin_w.close

        out_buf = +""
        err_buf = +""
        out_thread = Thread.new { out_buf << stdout_r.read.to_s }
        err_thread = Thread.new { err_buf << stderr_r.read.to_s }

        timed_out = false
        begin
          Timeout.timeout(WALL_TIMEOUT) { Process.wait(pid) }
        rescue Timeout::Error
          timed_out = true
          begin
            Process.kill(-9, pid) # negative pid == whole process group
            Process.wait(pid)
          rescue Errno::ESRCH, Errno::ECHILD
            nil
          end
        end

        out_thread.join(1)
        err_thread.join(1)
        [stdout_r, stderr_r].each { |io| io.close unless io.closed? }

        status = $?

        if timed_out
          err_buf << "\n[перервано: перевищено ліміт часу виконання (#{WALL_TIMEOUT}с)]"
        elsif status&.signaled? && [Signal.list["XCPU"], Signal.list["KILL"]].include?(status.termsig)
          err_buf << "\n[перервано: перевищено ліміт CPU (#{CPU_LIMIT}с) або пам'яті]"
        end

        [out_buf, err_buf, !timed_out && status&.success? == true]
      end

      def run_rubocop(source)
        Dir.mktmpdir("playground-lint-") do |dir|
          script_path = File.join(dir, "main.rb")
          File.write(script_path, source)

          stdout_str, stderr_str, status = Open3.capture3(
            "bundle", "exec", "rubocop",
            "--format", "json", "--force-default-config", "--no-color",
            script_path,
            chdir: dir
          )

          parsed = begin
            JSON.parse(stdout_str)
          rescue JSON::ParserError
            nil
          end

          if parsed.nil? || !status.success? && parsed.dig("files", 0).nil?
            next { success: false, offenses: [], error: stderr_str.presence || "RuboCop не зміг перевірити код" }
          end

          offenses = parsed.dig("files", 0, "offenses") || []
          {
            success: true,
            offenses: offenses.map { |o| format_offense(o) },
          }
        end
      rescue StandardError
        { success: false, offenses: [], error: "Внутрішня помилка сервера" }
      end

      def format_offense(offense)
        {
          line: offense.dig("location", "start_line") || offense.dig("location", "line") || 0,
          column: offense.dig("location", "start_column") || offense.dig("location", "column") || 0,
          severity: offense["severity"],
          message: offense["message"],
          cop_name: offense["cop_name"],
        }
      end

      def truncate(str)
        return str if str.bytesize <= MAX_OUTPUT_BYTES

        "#{str.byteslice(0, MAX_OUTPUT_BYTES)}\n…(вивід обрізано)"
      end
    end
  end
end
