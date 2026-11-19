"use strict";

(async function latestApk() {
  const button = document.getElementById("android-download");
  const status = document.getElementById("android-status");
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    const response = await fetch("https://api.github.com/repos/smartschoolkatten-star/SmartschoolKattenPatcher/releases/latest", {
      headers: { Accept: "application/vnd.github+json" }, signal: controller.signal
    });
    if (response.status === 404) {
      status.textContent = "Er is momenteel geen APK beschikbaar.";
      button.textContent = "Nog niet beschikbaar";
      return;
    }
    if (!response.ok) throw new Error("Release unavailable");
    const release = await response.json();
    if (release.draft || release.prerelease || !/^katten-r[1-9][0-9]{0,5}$/.test(release.tag_name)) return;
    const assets = (release.assets || []).filter(asset => asset.name === "Smartschool-Katten.apk");
    if (assets.length !== 1 || !/^sha256:[a-f0-9]{64}$/.test(assets[0].digest || "")) return;
    const url = new URL(assets[0].browser_download_url);
    if (url.origin !== "https://github.com" || !url.pathname.startsWith("/smartschoolkatten-star/SmartschoolKattenPatcher/releases/download/") || url.username || url.password) return;
    button.href = url.href;
    button.removeAttribute("aria-disabled");
    button.textContent = "Download APK";
    status.textContent = "Android 12L of nieuwer · " + release.tag_name.replace("katten-", "");
  } catch (_) {
    status.textContent = "Downloadinformatie is even niet beschikbaar.";
    button.href = "https://github.com/smartschoolkatten-star/SmartschoolKattenPatcher/releases";
    button.removeAttribute("aria-disabled");
    button.textContent = "Bekijk Android-versies";
  } finally { clearTimeout(timeout); }
})();
