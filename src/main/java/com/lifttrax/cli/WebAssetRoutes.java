package com.lifttrax.cli;

import static com.lifttrax.cli.WebServerCli.*;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/** Serves PWA assets and health responses. */
final class WebAssetRoutes {
  private WebAssetRoutes() {}

  static void handleManifest(HttpExchange exchange) throws IOException {
    sendContent(
        exchange,
        "application/manifest+json; charset=utf-8",
        """
            {
              "name": "LiftTrax",
              "short_name": "LiftTrax",
              "start_url": "/",
              "scope": "/",
              "display": "standalone",
              "theme_color": "#0f766e",
              "background_color": "#070d1a",
              "icons": [
                {
                  "src": "/pwa-icon.svg",
                  "sizes": "any",
                  "type": "image/svg+xml",
                  "purpose": "any maskable"
                }
              ]
            }
            """);
  }

  static void handleServiceWorker(HttpExchange exchange) throws IOException {
    sendContent(
        exchange,
        "text/javascript; charset=utf-8",
        """
            const CACHE_NAME = 'lifttrax-static-v1';
            const STATIC_ASSETS = [
              '/manifest.webmanifest',
              '/offline.html',
              '/pwa-icon.svg'
            ];

            self.addEventListener('install', (event) => {
              event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS)));
              self.skipWaiting();
            });

            self.addEventListener('activate', (event) => {
              event.waitUntil(self.clients.claim());
            });

            self.addEventListener('fetch', (event) => {
              if (event.request.method !== 'GET') {
                return;
              }
              const url = new URL(event.request.url);
              if (STATIC_ASSETS.includes(url.pathname)) {
                event.respondWith(
                  caches.match(event.request).then((cached) => cached || fetch(event.request))
                );
                return;
              }
              if (event.request.mode === 'navigate') {
                event.respondWith(fetch(event.request).catch(() => caches.match('/offline.html')));
              }
            });
            """);
  }

  static void handleOffline(HttpExchange exchange) throws IOException {
    sendHtml(
        exchange,
        WebHtml.wrapPage(
            "LiftTrax Offline",
            "<h1>LiftTrax</h1><p>The app is offline. Reconnect to view or save training data.</p>"));
  }

  static void handlePwaIcon(HttpExchange exchange) throws IOException {
    sendContent(
        exchange,
        "image/svg+xml; charset=utf-8",
        """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
              <rect width="512" height="512" rx="96" fill="#070d1a"/>
              <path d="M96 292h320v56H96zM128 204h256v56H128z" fill="#14b8a6"/>
              <path d="M176 140h56v232h-56zM280 140h56v232h-56z" fill="#facc15"/>
            </svg>
            """);
  }

  static void handleHealth(HttpExchange exchange) throws IOException {
    WebRequestSecurity.exposeCsrfToken(exchange);
    sendText(exchange, 200, "ok");
  }
}
