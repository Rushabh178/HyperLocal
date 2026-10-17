# Test server on AWS (EC2, free plan)

One small EC2 machine runs the API and PostgreSQL/PostGIS in Docker. It is meant to be **started only
while testing** (a few hours a week) and stopped otherwise; data survives stops.

Rough cost at ~8 running hours a month (Mumbai, t3.small): ~$3/month, ~$6 with an Elastic IP.

## 1. AWS account (once)

1. Sign up at aws.amazon.com and pick the **Free plan**. Top-right region: **Asia Pacific (Mumbai)**.
2. **Billing and Cost Management → Budgets → Create budget**: monthly cost budget of **$10**, with credits
   *excluded* (so it measures real usage), email alert at 80%. This catches a server left running.

## 2. Launch the server (once)

**EC2 → Launch instance**

| Setting | Value |
|---|---|
| Name | `hyperlocal-test` |
| AMI | **Ubuntu Server 24.04 LTS** (x86_64) |
| Instance type | **t3.small** |
| Key pair | Create new → `hyperlocal-test`, `.pem`; keep the downloaded file safe |
| Network | Allow SSH from **My IP**; allow **HTTP** from the internet |
| Storage | **20 GB gp3** |

Then pick an address the apps will use:

- **Elastic IP** (fixed, ~$3.60/month): EC2 → Elastic IPs → Allocate → Actions → Associate → this instance.
- **DuckDNS** (free): sign in at duckdns.org, create a name (e.g. `hyperlocal-test`), copy the token.
  Pass both to the setup script in step 4.

## 3. Push this folder to GitHub

The server pulls the code from GitHub, so commit and push the `deploy/` folder first.

## 4. Set up the server (once)

Connect: EC2 → select the instance → **Connect → EC2 Instance Connect** (browser terminal), or from
PowerShell:

```powershell
ssh -i C:\path\to\hyperlocal-test.pem ubuntu@<public-ip>
```

On the server:

```bash
git clone https://github.com/Rushabh178/HyperLocal.git
cd HyperLocal
# Private repo: use a GitHub personal access token as the password.

sudo bash deploy/setup-ec2.sh
# With DuckDNS instead of an Elastic IP:
# sudo DUCKDNS_DOMAIN=hyperlocal-test DUCKDNS_TOKEN=xxxx bash deploy/setup-ec2.sh

exit   # log out and back in so your user can run docker
```

Reconnect, then:

```bash
cd HyperLocal/deploy
cp .env.example .env
nano .env     # DB_PASSWORD, JWT_SECRET, PUBLIC_BASE_URL (see the comments in the file)
docker compose up -d --build     # first build takes ~5-10 minutes
docker compose logs -f app       # wait for "Started AppApplication", then Ctrl+C
```

The API is now at `http://<elastic-ip>` or `http://<name>.duckdns.org` (port 80). Point the mobile apps
there.

## Every testing session

1. **EC2 → Instances → select → Instance state → Start.** The database and API come up by themselves in
   about a minute.
2. Test. OTPs and pushes are printed to the log (`dev` profile):
   ```bash
   cd HyperLocal/deploy && docker compose logs -f app | grep -E "DEV OTP|DEV PUSH"
   ```
3. **Instance state → Stop** when done. If you forget, the server stops itself 3 hours after it
   started (`AUTO_STOP_MINUTES`). Need longer once? `sudo shutdown -c` cancels it until the next boot.

Without an Elastic IP or DuckDNS the public IP changes on every start: update `PUBLIC_BASE_URL` in
`.env` (then `docker compose up -d`) and the apps.

## Deploying new code

```bash
cd HyperLocal && git pull && cd deploy && docker compose up -d --build
```

## Useful commands

```bash
docker compose ps                    # are both containers up?
docker compose logs --tail 200 app   # recent API logs
docker compose restart app           # restart the API only
docker compose exec db psql -U postgres hyper_local   # database shell
```

## Notes

- **Never stop or terminate data by accident:** *Stop* keeps everything; *Terminate* deletes the
  server and its disk.
- The `dev` profile means fake payments, seeded demo stores (password `Demo@1234`) and OTPs in the log.
  Fine for testing; switch profiles before real users use this server.
- The API is plain HTTP. Android blocks cleartext HTTP unless the debug build allows it
  (`usesCleartextTraffic` / network security config); iOS needs an ATS exception. HTTPS needs a domain
  (DuckDNS works) and a reverse proxy such as Caddy, which can be added later.
- The free plan ends after 6 months or when credits run out; upgrade the account before then or it is
  closed.
