/**
 * Hash the wheel files named by catalogue/steering-packages.json.
 * Writes catalogue/pack-manifest.json and copies template JSON into
 * website/public/packs/files so a Worker assets binding can serve it.
 * PNG files stay in website/public/wheels and are not duplicated.
 */
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '../..');
const catalogue = JSON.parse(readFileSync(join(root, 'catalogue/steering-packages.json'), 'utf8'));
const wheelsDir = join(root, 'app/src/main/assets/wheels');
const outDir = join(root, 'website/public/packs/files');
mkdirSync(outDir, { recursive: true });

const ids = new Set();
for (const pack of [...catalogue.manufacturers, ...catalogue.classes]) {
  for (const id of pack.templates) ids.add(id);
}

const files = {};
const templates = {};
const missing = [];
for (const id of [...ids].sort()) {
  const jsonName = `${id}.json`;
  const jsonPath = join(wheelsDir, jsonName);
  const names = [];
  let image = '';
  try {
    const parsed = JSON.parse(readFileSync(jsonPath, 'utf8'));
    image = String(parsed.image || '');
    const bytes = readFileSync(jsonPath);
    files[jsonName] = { sha256: createHash('sha256').update(bytes).digest('hex'), bytes: bytes.length };
    writeFileSync(join(outDir, jsonName), bytes);
    names.push(jsonName);
  } catch {
    missing.push(jsonName);
  }
  if (image) {
    const imagePath = join(wheelsDir, image);
    try {
      const bytes = readFileSync(imagePath);
      files[image] = { sha256: createHash('sha256').update(bytes).digest('hex'), bytes: bytes.length };
      names.push(image);
    } catch {
      missing.push(image);
    }
  }
  templates[id] = names;
}

const manifest = { version: 1, templates, files };
writeFileSync(join(root, 'catalogue/pack-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`hashed ${Object.keys(files).length} files`);
if (missing.length) {
  console.error('missing', missing.join(', '));
  process.exit(1);
}
