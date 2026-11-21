import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

export async function waitForChromePort(profile, { timeoutMs = 10000, pollIntervalMs = 100 } = {}) {
  const path = join(profile, 'DevToolsActivePort');
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const contents = await readFile(path, 'utf8');
      const port = Number(contents.split('\n')[0]);
      // Chrome can create the file before writing it. A newline marks a complete port.
      if (contents.includes('\n') && Number.isInteger(port) && port > 0 && port <= 65535) {
        return port;
      }
    } catch (error) {
      if (error.code !== 'ENOENT') {throw error;}
    }
    await delay(pollIntervalMs);
  }
  throw new Error(`Timed out after ${timeoutMs}ms waiting for a valid Chrome debugging port in ${path}`);
}
