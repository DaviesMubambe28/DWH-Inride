const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

if (reducedMotion) {
  document.querySelectorAll(".reveal").forEach((element) => element.classList.add("visible"));
} else {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.13 },
  );

  document.querySelectorAll(".reveal").forEach((element) => observer.observe(element));
}

const toast = document.querySelector(".toast");
let toastTimer;

document.querySelectorAll("[data-store]").forEach((button) => {
  button.addEventListener("click", () => {
    clearTimeout(toastTimer);
    toast.textContent = `${button.dataset.store} release coming soon in Chongwe.`;
    toast.classList.add("show");
    toastTimer = setTimeout(() => toast.classList.remove("show"), 3200);
  });
});

document.querySelector("#year").textContent = new Date().getFullYear();
