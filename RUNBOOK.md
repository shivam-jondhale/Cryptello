# Operational Runbook

## 1. Deployment & Hardening
### Nginx Reference Config (WAF/Rate Limits)
```nginx
http {
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
    
    server {
        listen 443 ssl;
        server_name api.cryptello.com;
        
        # SSL
        ssl_certificate /etc/letsencrypt/live/api.cryptello.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/api.cryptello.com/privkey.pem;
        
        # Security Headers
        add_header X-Frame-Options "DENY";
        add_header Content-Security-Policy "default-src 'self'";
        
        location / {
            proxy_pass http://localhost:5454;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }

        # Rate Limited Zones
        location /api/auth {
            limit_req zone=api_limit burst=5 nodelay;
            proxy_pass http://localhost:5454;
        }
    }
}
```

### Environment Variables
Set the following before starting:
```bash
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET=...
export DB_URL=...
export DB_USER=...
export DB_PASSWORD=...
export STRIPE_API_KEY=...
export STRIPE_WEBHOOK_SECRET=...
export SENTRY_DSN=...
```

## 2. Incident Management

### 🔴 Payment Provider Outage
**Detection**: Spike in 500 errors on `/api/payments`. Sentry alerts for `StripeException`.
**Immediate Action**:
1. Check Stripe/Razorpay Status Page.
2. If major outage, enable "Maintenance Mode" for payments (Future Feature) or notify users via banner.
**Post-Incident**:
- Reconcile missed webhooks once service is up.

### 🟠 Webhook Failures
**Detection**:
- Prometheus: `cryptello_webhook_error_total` > 0.
- Logs: `Failed to process webhook event`.
**Investigation**:
```sql
SELECT * FROM webhook_events WHERE status = 'FAILED' ORDER BY received_at DESC;
```
**Remediation**:
1. Fix the bug (if code issue).
2. Reset status to `PENDING` to retry (simulated replay):
   ```sql
   UPDATE webhook_events SET status = 'PENDING' WHERE id = ?;
   ```
   *Note: Ensure the worker picks up pending events or manually trigger a job.*

### 🛡️ DDoS / High Load
**Detection**:
- `cryptello_ratelimit_hit_total` spiking.
- CPU > 80%.
- Latency > 2s.
**Mitigation**:
1. Enable "Under Attack Mode" in Cloudflare (if used).
2. Tighten Nginx rate limits.
3. Block offending IPs in `IpBanService` (via DB or future Admin UI).

### 🔑 Key Rotation
**JWT Secret**:
1. Generate new secret.
2. Update `JWT_SECRET` env var.
3. Restart App. *Warning: invalidates all current sessions.*

**Stripe Keys**:
1. Roll key in Stripe Dashboard.
2. Update `STRIPE_API_KEY` & `STRIPE_WEBHOOK_SECRET`.
3. Restart App.

