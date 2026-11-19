import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const script = readFileSync(new URL('../site/downloads.js', import.meta.url), 'utf8');
async function check(response, expected) {
  const button = { attrs: { 'aria-disabled': 'true' }, textContent: 'Binnenkort beschikbaar', removeAttribute(name) { delete this.attrs[name]; } };
  const status = { textContent: 'De APK wordt nog getest.' };
  await vm.runInNewContext(script, {
    document: { getElementById: id => id === 'android-download' ? button : status },
    fetch: async () => { if (response instanceof Error) throw response; return response; },
    AbortController, URL, setTimeout, clearTimeout
  });
  assert.equal(button.href, expected);
  if (expected) assert.equal(button.attrs['aria-disabled'], undefined);
  else assert.equal(button.attrs['aria-disabled'], 'true');
}
const url = 'https://github.com/smartschoolkatten-star/SmartschoolKattenPatcher/releases/download/katten-r46/Smartschool-Katten.apk';
const release = { tag_name: 'katten-r46', draft: false, prerelease: false, assets: [{ name: 'Smartschool-Katten.apk', browser_download_url: url, digest: 'sha256:' + 'a'.repeat(64) }] };
const ok = value => ({ ok: true, status: 200, json: async () => value });
await check({ status: 404 }, undefined);
await check(ok(release), url);
await check(ok({ ...release, prerelease: true }), undefined);
await check(ok({ ...release, draft: true }), undefined);
await check(ok({ ...release, tag_name: 'patch-r46' }), undefined);
await check(ok({ ...release, assets: [] }), undefined);
await check(ok({ ...release, assets: [release.assets[0], release.assets[0]] }), undefined);
await check(ok({ ...release, assets: [{ ...release.assets[0], digest: '' }] }), undefined);
await check(ok({ ...release, assets: [{ ...release.assets[0], browser_download_url: 'https://example.com/app.apk' }] }), undefined);
await check(new Error('network unavailable'), 'https://github.com/smartschoolkatten-star/SmartschoolKattenPatcher/releases');
const html = readFileSync(new URL('../site/index.html', import.meta.url), 'utf8');
assert.match(html, /lang="nl"/);
assert.match(html, /https:\/\/chromewebstore\.google\.com\/detail\/smartschool-katten\/ndembkilcdoipaefhmbnoiamccnkjgco/);
assert.match(html, /https:\/\/addons\.mozilla\.org\/en-US\/firefox\/addon\/smartschool-katten\//);
console.log('PASS download page: store links, valid APK, unpublished/pre-release/malformed assets and offline fallback');
