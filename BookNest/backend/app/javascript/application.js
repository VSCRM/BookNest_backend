// BookNest — entry point for the Rails part's frontend JS (Hotwire Turbo).
// The Playground (labs 5-7) is a separate React/Vite app and is not part of this file.
import "@hotwired/turbo-rails";

// Events on which the page needs to be re-initialized: the first page
// load, and every subsequent Turbo navigation (Turbo Drive swaps out
// <body> without a full document reload, so DOMContentLoaded only fires
// once per session).
const REBIND_EVENTS = ["DOMContentLoaded", "turbo:load"];

function initBurgerMenu() {
  const burger = document.querySelector(".nav-burger");
  const nav = document.getElementById("main-nav");
  if (!burger || !nav) return;

  burger.addEventListener("click", () => {
    const isOpen = nav.classList.toggle("is-open");
    burger.setAttribute("aria-expanded", String(isOpen));
  });

  // Hover doesn't work on mobile — the "For developers" dropdown opens on tap instead.
  const dropdownBtn = nav.querySelector(".nav-dropdown-btn");
  const dropdown = nav.querySelector(".nav-dropdown");
  if (dropdownBtn && dropdown) {
    dropdownBtn.addEventListener("click", (e) => {
      if (window.matchMedia("(max-width: 720px)").matches) {
        e.preventDefault();
        dropdown.classList.toggle("is-open");
      }
    });
  }
}

// == Minimal real-time form validation (register/login) ====================
// Hints appear under the field as soon as the user starts typing, without
// reloading the page — similar to how modern SPA forms (React) behave —
// but implemented as plain vanilla JS with no separate frontend build,
// since Rails itself renders these pages.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function fieldHint(input) {
  let hint = input.parentElement.querySelector(".field-hint");
  if (!hint) {
    hint = document.createElement("small");
    hint.className = "field-hint";
    input.insertAdjacentElement("afterend", hint);
  }
  return hint;
}

function setFieldState(input, message) {
  const hint = fieldHint(input);
  if (message) {
    input.classList.add("input--invalid");
    input.classList.remove("input--valid");
    hint.textContent = message;
    hint.classList.add("field-hint--error");
  } else {
    input.classList.remove("input--invalid");
    input.classList.add("input--valid");
    hint.textContent = "";
    hint.classList.remove("field-hint--error");
  }
}

function validateEmailField(input) {
  if (!input.value) return setFieldState(input, "");
  setFieldState(input, EMAIL_RE.test(input.value) ? "" : "Схоже, це не email (потрібен формат name@example.com)");
}

function validatePasswordField(input, { minLength = 6 } = {}) {
  if (!input.value) return setFieldState(input, "");
  if (input.value.length < minLength) {
    setFieldState(input, `Ще щонайменше ${minLength - input.value.length} символ(и) до мінімуму в ${minLength}`);
    return;
  }
  setFieldState(input, "");
}

function validateRequiredField(input, label) {
  if (!input.value.trim()) return setFieldState(input, "");
  setFieldState(input, input.value.trim().length < 2 ? `${label} — закоротко` : "");
}

function initAuthValidation() {
  const form = document.querySelector(".auth-form form");
  if (!form) return;

  const nameInput = form.querySelector('input[name="user[name]"]');
  const emailInput = form.querySelector('input[type="email"]');
  const passwordInput = form.querySelector('input[type="password"]');

  nameInput?.addEventListener("input", () => validateRequiredField(nameInput, "Ім'я"));
  emailInput?.addEventListener("input", () => validateEmailField(emailInput));
  passwordInput?.addEventListener("input", () => validatePasswordField(passwordInput));
}

REBIND_EVENTS.forEach((evt) => {
  document.addEventListener(evt, () => {
    initBurgerMenu();
    initAuthValidation();
  });
});
