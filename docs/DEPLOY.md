# Deploying the demo

The whole application runs on one small Linux VM with Docker Compose: MySQL, Redis, the backend and both Nginx frontends, started from `compose.production.yml` plus the 1 GiB settings in [deploy/compose.small-host.yml](../deploy/compose.small-host.yml). GitHub Actions builds the images and publishes them to GitHub Container Registry, so the VM only pulls them. Building them on a small burstable VM would be slow and would use up its CPU credits.

Nothing else is created: no managed database, cache, load balancer, NAT gateway or domain. Each of those has a monthly price of its own.

## Where to run it

| Cloud | Cost | Launcher |
| --- | --- | --- |
| Azure for Students | Covered for 12 months. The VM size and two 64 GiB SSDs are among the 12-month free services, and the public IP, about $3.65 a month, comes out of the $100 credit. No credit card; renewable every year while you are a student. | [deploy/azure/launch.sh](../deploy/azure/launch.sh) |
| AWS | About $12.84 a month in us-east-1. Accounts opened on or after 15 July 2025 get credits that cover it for up to 6 months on the Free plan. | [deploy/aws/launch.sh](../deploy/aws/launch.sh) |

Both launchers boot the VM with [deploy/first-boot.sh](../deploy/first-boot.sh), which works on Ubuntu and Amazon Linux 2023 on x86_64, so any VM of that kind with 1 GiB of memory can run the demo.

## Before you start

1. **Publish the images.** The `Publish Docker images` workflow runs when changes reach `main`.
2. **Make the images public.** On GitHub, open each package (`sky-backend`, `sky-admin-web`, `sky-customer-web`), then Package settings, Change visibility, Public. The VM then pulls without credentials. The images contain only what this public repository contains.

## Azure for Students

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

## AWS

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

## First boot

The first boot takes about 5 to 10 minutes. `first-boot.sh` installs Docker and the Compose plugin, adds 2 GiB of swap, clones this repository to `/opt/sky-take-out`, and runs [deploy/setup.sh](../deploy/setup.sh), which:

- writes `.env` with new random database, Redis and JWT secrets, and turns on the simulated checkout
- starts MySQL, which creates the schema and demo menu from `docker/mysql/init`
- replaces the seeded merchant password `123456` with a random one before anything is reachable
- starts the backend and both frontends, and waits until all of them are healthy

The log is `/var/log/cloud-init-output.log`; it ends with `Sky Take-Out is running.` Then open the customer app at `http://<public IP>/` and the merchant portal at `http://<public IP>:8081/`. The merchant user is `admin`, and its password is in `/opt/sky-take-out/admin-password.txt`.

If the first boot stopped partway, fix the cause and run `sudo bash /opt/sky-take-out/deploy/setup.sh` on the VM. It keeps whatever was already done.

## Updating

On the VM:

```bash
cd /opt/sky-take-out
sudo git pull
sudo docker compose pull
sudo docker compose up -d
```

`.env`, the database and the merchant password are kept. A release that adds a schema file under `docker/mysql/init` needs that file applied once, as described in [docker/mysql/init/README.md](../docker/mysql/init/README.md). Useful checks: `sudo docker compose ps`, `sudo docker compose logs -f backend`, `free -m`, and `sudo docker stats --no-stream`.

## Limitations of this setup

- **HTTP only.** There is no domain or TLS certificate, so browsers mark the pages "Not secure".
- **No backups.** Everything lives on one disk. A snapshot is a cheap copy if the data matters.
- **Simulated payments**, as in the local demo: customers mark their own orders paid, and no money moves.
- **Public merchant portal.** Anyone with the password can change the menu and orders.
- **Image upload needs Aliyun OSS.** The demo menu's photos ship with the frontends, but a merchant who uploads a new dish photo needs Aliyun OSS credentials.
