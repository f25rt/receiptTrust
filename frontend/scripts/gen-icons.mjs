// Rasterizes the source SVGs into the PNG icons the PWA manifest needs.
// Run: node scripts/gen-icons.mjs
import sharp from 'sharp';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const pub = resolve(__dirname, '..', 'public');

const jobs = [
  { src: 'icon.svg', out: 'pwa-192x192.png', size: 192 },
  { src: 'icon.svg', out: 'pwa-512x512.png', size: 512 },
  { src: 'icon-maskable.svg', out: 'pwa-maskable-512x512.png', size: 512 },
  { src: 'icon.svg', out: 'apple-touch-icon.png', size: 180 },
  { src: 'icon.svg', out: 'favicon-32x32.png', size: 32 },
];

for (const job of jobs) {
  const svg = readFileSync(resolve(pub, job.src));
  await sharp(svg, { density: 384 })
    .resize(job.size, job.size)
    .png()
    .toFile(resolve(pub, job.out));
  console.log(`wrote public/${job.out} (${job.size}x${job.size})`);
}
