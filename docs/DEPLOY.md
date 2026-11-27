# Deploying the demo

The demo can go online in two ways, and both can be free for a student:

| | Render, Vercel and TiDB Cloud | One VM |
| --- | --- | --- |
| Cost | Free plans, no credit card | Azure for Students covers it for 12 months; about $12.84 a month on AWS |
| Address | `https://<project>.vercel.app`, with HTTPS | `http://<public IP>/`, without HTTPS |
| When nobody uses it | The backend stops after 15 minutes, and the next visitor waits about 2 minutes for it | Always running |
| Database | TiDB Cloud Starter, which speaks the MySQL protocol | MySQL 8 on the VM |
| Setup | Three accounts, one script on your machine, then a few clicks | One script in the cloud's browser shell |

GitHub Actions builds the images and publishes them to GitHub Container Registry, so neither way builds the backend itself.

## Before you start

1. **Publish the images.** The `Publish Docker images` workflow runs when changes reach `main`.
2. **Make the images public.** On GitHub, open each package (`sky-backend`, `sky-admin-web`, `sky-customer-web`), then Package settings, Change visibility, Public. Render and the VM then pull without credentials. The images contain only what this public repository contains.

## Render, Vercel and TiDB Cloud

| Part | Runs on | Free plan |
| --- | --- | --- |
| Customer app and merchant portal | Vercel, one project each | Hobby, for personal and non-commercial projects |
| Backend | Render web service, from the `sky-backend` image | 512 MB of memory, 0.1 CPU, 750 instance hours a month |
| Redis | Render Key Value | 25 MB, no persistence |
| Database | TiDB Cloud Starter, AWS Oregon | 5 GiB of row storage and 50 million Request Units a month |

The browser loads both apps from Vercel, and Vercel forwards their API requests to the backend: the rewrites in [sky-frontend-user/vercel.json](../sky-frontend-user/vercel.json) and [sky-frontend-admin/vercel.json](../sky-frontend-admin/vercel.json) send `/user/*`, `/notify/*` and `/api/*` where the Nginx configurations send them in Docker, and turn off Vercel's caching for those paths. Vercel cannot forward WebSocket connections, so the merchant portal's order alerts connect straight to `wss://sky-take-out-api.onrender.com/ws`, which its build command sets. [render.yaml](../render.yaml) describes the backend and the Key Value instance.

### 1. Database

1. Sign up at [tidbcloud.com](https://tidbcloud.com) and create a cluster on the **Starter** plan, on AWS in **Oregon (us-west-2)**, where render.yaml puts the backend. Every request makes several queries, so the two must be in the same region.
2. Open the cluster and choose **Connect**. Note the host and the user, which looks like `xxxxxxxx.root`, and generate a password.
3. In a clone of this repository, with Docker running, run:

   ```bash
   bash deploy/init-database.sh
   ```

   On Windows, run it in Git Bash. It asks for the host, user and password, then creates the `sky_take_out` database from the same files MySQL uses ([docker/mysql/init](../docker/mysql/init)). It also adds a user for the backend that can read and change rows but not tables, and replaces the seeded merchant password `123456` with a random one. At the end it prints the database settings for Render, and the merchant password, which is also saved in `admin-password.txt` in the clone. It refuses a database that already has tables.

### 2. Backend

1. Sign up at [render.com](https://render.com). Choose **New**, then **Blueprint**, and pick this repository.
2. Render reads render.yaml and asks for `DB_HOST`, `DB_USERNAME` and `DB_PASSWORD`. Enter the three values the script printed, then deploy. Render creates the web service `sky-take-out-api` and the Key Value instance `sky-take-out-cache`, generates the two JWT secrets, and turns on the simulated checkout.
3. The first deploy takes a few minutes, most of them Spring Boot starting. Then `https://sky-take-out-api.onrender.com/user/shop/status` answers `{"code":1,"msg":null,"data":1}`.

If Render gives the service another address, because someone else already has that name, replace `sky-take-out-api.onrender.com` with it in both `vercel.json` files and push.

### 3. Frontends

1. Sign up at [vercel.com](https://vercel.com). Choose **Add New**, then **Project**, and import this repository with **Root Directory** set to `sky-frontend-user`. Vercel detects Vite; deploy.
2. Import the repository a second time with the root directory `sky-frontend-admin`.

The project names become the addresses, such as `https://sky-take-out.vercel.app/` for the customer app. The merchant user is `admin`, with the password from step 1.

### What to expect

- **The backend sleeps.** Render stops a free web service after 15 minutes without requests and starts it again on the next one. Spring Boot needs about 2 minutes to start on 0.1 CPU; render.yaml sets the JVM and Spring options that got it from 8 minutes down to that, measured locally with the same CPU and memory limits. Until it is up, the customer app shows an error, because it gives up on a request after 10 seconds. Reload after a couple of minutes.
- **Keeping it awake.** An uptime monitor that requests `https://sky-take-out-api.onrender.com/user/shop/status` every 10 minutes keeps the backend running. That uses at most 744 of the workspace's 750 free instance hours a month, so it works only while this is the workspace's one free web service.
- **Scheduled jobs pause** while the backend sleeps. Cancelling unpaid orders after 15 minutes and completing orders at night run again once it is up.
- **Redis can lose its data.** Render may restart a free Key Value instance at any time, and its data is gone. That costs nothing here: the backend reads the menu from the database again, and the shop counts as open.
- **Free plan limits.** Render suspends a workspace's free services when they use more bandwidth than the plan includes, until the next month, unless a card is on file.

### Updating

- **Frontends.** Vercel deploys both projects again on every push to `main`.
- **Backend.** Render does not redeploy an image-backed service by itself. After the `Publish Docker images` workflow finishes, open `sky-take-out-api` and choose **Manual Deploy**, then **Deploy latest reference**.
- **Database.** A release that adds a schema file under `docker/mysql/init` needs that file applied once, for example in the cluster's SQL Editor in the TiDB Cloud console.

### Clean up

Delete the two Render services, the two Vercel projects and the TiDB cluster.

## One VM

The whole application runs on one small Linux VM with Docker Compose: MySQL, Redis, the backend and both Nginx frontends, started from `compose.production.yml` plus the 1 GiB settings in [deploy/compose.small-host.yml](../deploy/compose.small-host.yml). The VM only pulls the images: building them on a small burstable VM would be slow and would use up its CPU credits.

Nothing else is created: no managed database, cache, load balancer, NAT gateway or domain. Each of those has a monthly price of its own.

| Cloud | Cost | Launcher |
| --- | --- | --- |
| Azure for Students | Covered for 12 months. The VM size and two 64 GiB SSDs are among the 12-month free services, and the public IP, about $3.65 a month, comes out of the $100 credit. No credit card; renewable every year while you are a student. | [deploy/azure/launch.sh](../deploy/azure/launch.sh) |
| AWS | About $12.84 a month in us-east-1. Accounts opened on or after 15 July 2025 get credits that cover it for up to 6 months on the Free plan. | [deploy/aws/launch.sh](../deploy/aws/launch.sh) |

Both launchers boot the VM with [deploy/first-boot.sh](../deploy/first-boot.sh), which works on Ubuntu and Amazon Linux 2023 on x86_64, so any VM of that kind with 1 GiB of memory can run the demo.

### Azure for Students

**Account.** Sign up at [azure.microsoft.com/free/students](https://azure.microsoft.com/en-us/free/students/) with your school email address. No credit card is needed. When the credit runs out, Azure disables the subscription instead of billing it, unless you choose to move to pay-as-you-go.

**Launch.** Sign in to [portal.azure.com](https://portal.azure.com), open Cloud Shell (the terminal icon in the top bar), choose Bash, and run:

```bash
curl -fsSLO https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main/deploy/azure/launch.sh
bash launch.sh
```

The script reads the regions the subscription may deploy to (Azure for Students allows only a few) and picks the first one that offers `Standard_B2ats_v2` (2 vCPU, 1 GiB), or else `Standard_B1s`. Then it asks before creating anything. On `y` it creates the resource group `sky-take-out` with the VM (Ubuntu 24.04, 64 GiB Premium SSD, static public IP) and opens ports 80 and 8081. It opens no SSH port. To run commands on the VM, use `az vm run-command` from Cloud Shell, as the script prints at the end:

```bash
az vm run-command invoke -g sky-take-out -n sky-take-out --command-id RunShellScript --query "value[0].message" -o tsv --scripts "tail -n 25 /var/log/cloud-init-output.log"
```

A day after launching, check Cost Management in the portal to confirm that only the public IP draws on the credit.

**Clean up.** `az group delete --name sky-take-out` deletes the VM, its disk, its IP address and everything else the launcher created.

### AWS

On-demand prices in us-east-1, November 2026:

| Item | Price | Per month |
| --- | ---: | ---: |
| t3.micro instance (2 vCPU, 1 GiB) | $0.0104 per hour | $7.59 |
| 20 GiB gp3 root volume | $0.08 per GiB-month | $1.60 |
| Public IPv4 address | $0.005 per hour | $3.65 |
| Data transfer out | first 100 GB per month free | $0 |
| **Total** | | **about $12.84** |

New accounts get $100 in credits at sign-up and up to $100 more for trying listed services. On the Free plan the account is never billed, and it closes after 6 months or when the credits run out. Older accounts are past their 12-month free tier and pay the prices above. Set a monthly cost budget of $15 with an email alert; an alert then means something else is running. T3 instances launch with unlimited CPU credits, which an idle demo never touches, so do not load-test the instance.

**Launch from CloudShell.** In the AWS console with **US East (N. Virginia)** selected, open CloudShell (the terminal icon in the top bar) and run:

```bash
curl -fsSLO https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main/deploy/aws/launch.sh
bash launch.sh
```

It finds the default VPC, a subnet whose zone offers t3.micro, and the latest Amazon Linux 2023 image, then asks before creating anything. On `y` it creates the `sky-take-out` security group (80 and 8081 open; SSH only through EC2 Instance Connect), launches the instance with a 20 GiB gp3 volume, and prints the addresses. It will not start a second instance named `sky-take-out`.

**Or use the launch wizard** with: Amazon Linux 2023 (64-bit x86), `t3.micro`, a security group allowing 80 and 8081 from anywhere and SSH from the prefix list `com.amazonaws.us-east-1.ec2-instance-connect`, 20 GiB gp3, and the entire contents of [deploy/first-boot.sh](../deploy/first-boot.sh) as User data.

**A stable address.** The public IPv4 address changes when the instance is stopped and started. An Elastic IP keeps it; it costs the same while attached to a running instance, but is also billed while the instance is stopped, until released.

**Clean up.** Stopping the instance ends the instance and IPv4 charges; the volume keeps billing. Terminating it ends all charges and deletes the volume.

### First boot

The first boot takes about 5 to 10 minutes. `first-boot.sh` installs Docker and the Compose plugin, adds 2 GiB of swap, clones this repository to `/opt/sky-take-out`, and runs [deploy/setup.sh](../deploy/setup.sh), which:

- writes `.env` with new random database, Redis and JWT secrets, and turns on the simulated checkout
- starts MySQL, which creates the schema and demo menu from `docker/mysql/init`
- replaces the seeded merchant password `123456` with a random one before anything is reachable
- starts the backend and both frontends, and waits until all of them are healthy

The log is `/var/log/cloud-init-output.log`; it ends with `Sky Take-Out is running.` Then open the customer app at `http://<public IP>/` and the merchant portal at `http://<public IP>:8081/`. The merchant user is `admin`, and its password is in `/opt/sky-take-out/admin-password.txt`.

If the first boot stopped partway, fix the cause and run `sudo bash /opt/sky-take-out/deploy/setup.sh` on the VM. It keeps whatever was already done.

### Updating the VM

On the VM:

```bash
cd /opt/sky-take-out
sudo git pull
sudo docker compose pull
sudo docker compose up -d
```

`.env`, the database and the merchant password are kept. A release that adds a schema file under `docker/mysql/init` needs that file applied once, as described in [docker/mysql/init/README.md](../docker/mysql/init/README.md). Useful checks: `sudo docker compose ps`, `sudo docker compose logs -f backend`, `free -m`, and `sudo docker stats --no-stream`.

### Limitations of the VM

- **HTTP only.** There is no domain or TLS certificate, so browsers mark the pages "Not secure".
- **No backups.** Everything lives on one disk. A snapshot is a cheap copy if the data matters.

## Limitations of both setups

- **Simulated payments**, as in the local demo: customers mark their own orders paid, and no money moves.
- **Public merchant portal.** Anyone with the password can change the menu and orders.
- **Image upload needs Aliyun OSS.** The demo menu's photos ship with the frontends, but a merchant who uploads a new dish photo needs Aliyun OSS credentials.
