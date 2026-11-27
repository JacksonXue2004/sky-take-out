# Deploying to AWS on the Free Tier

The whole application runs on one EC2 instance with Docker Compose: MySQL, Redis, the backend and both Nginx frontends, started from `compose.production.yml` plus the small-instance settings in [deploy/aws/compose.ec2.yml](../deploy/aws/compose.ec2.yml). GitHub Actions builds the images and publishes them to GitHub Container Registry, so the instance only pulls them. Building them on a 1 GiB burstable instance would be slow and would use up its CPU credits.

Nothing else is created: no RDS, ElastiCache, load balancer, NAT gateway or domain. Each of those has a monthly price of its own.

## Cost

On-demand prices in us-east-1, November 2026. Check the current prices before relying on them.

| Item | Price | Per month |
| --- | ---: | ---: |
| t3.micro instance (2 vCPU, 1 GiB) | $0.0104 per hour | $7.59 |
| 20 GiB gp3 root volume | $0.08 per GiB-month | $1.60 |
| Public IPv4 address | $0.005 per hour | $3.65 |
| Data transfer out | first 100 GB per month free | $0 |
| **Total** | | **about $12.84** |

- **Accounts opened on or after 15 July 2025** get $100 in credits at sign-up and up to $100 more for trying listed services. On the Free plan, AWS does not bill the account; the account closes after 6 months or when the credits run out, whichever comes first. Six months of this instance use about $77 of credits.
- **Older accounts** had a 12-month free tier, which has ended for all of them, so they pay the prices above.
- **A t3.small** (2 GiB) costs $15.18 per month for the instance, about $20.43 in total, and six months use about $123. Switch to it if the t3.micro runs short of memory: stop the instance, change its type, and start it again. The data stays.

T3 instances launch with unlimited CPU credits. An idle demo averages far below the 10% baseline, so that costs nothing extra; sustained high CPU does, so do not load-test this instance.

## Before you start

1. **Publish the images.** The `Publish Docker images` workflow runs when changes reach `main`.
2. **Make the images public.** On GitHub, open each package (`sky-backend`, `sky-admin-web`, `sky-customer-web`), then Package settings, Change visibility, Public. The instance then pulls without credentials. The images contain only what this public repository contains.
3. **Set a budget.** In Billing and Cost Management, create a monthly cost budget of $15 with an email alert. This instance stays near $13, so an alert means something else is running.

## Launch the instance

### From CloudShell

Wait until the `Publish Docker images` run for the latest `main` has finished, or the instance pulls the previous images. Then, in the AWS console with **US East (N. Virginia)** selected, open CloudShell (the terminal icon in the top bar) and run:

```bash
curl -fsSLO https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main/deploy/aws/launch.sh
bash launch.sh
```

[deploy/aws/launch.sh](../deploy/aws/launch.sh) looks up the default VPC, a subnet whose zone offers t3.micro, and the latest Amazon Linux 2023 image, then asks before creating anything. On `y` it creates the `sky-take-out` security group (80 and 8081 open; SSH only through EC2 Instance Connect), launches the instance with a 20 GiB gp3 volume and [deploy/aws/user-data.sh](../deploy/aws/user-data.sh), and prints the addresses. It will not start a second instance named `sky-take-out`.

### From the launch wizard

In the EC2 console, in **US East (N. Virginia)**, choose **Launch instance**:

| Setting | Value |
| --- | --- |
| Name | `sky-take-out` |
| AMI | Amazon Linux 2023, 64-bit (x86). The images are built for `linux/amd64` only. |
| Instance type | `t3.micro` |
| Key pair | One you already have, to connect with SSH from your computer. Without one, use EC2 Instance Connect from the console. |
| Security group | SSH (22) from My IP; HTTP (80) from anywhere; Custom TCP 8081 from anywhere for the merchant portal, or from My IP to keep it private. |
| Storage | 20 GiB, gp3 |
| Advanced details, User data | The entire contents of [deploy/aws/user-data.sh](../deploy/aws/user-data.sh) |

EC2 Instance Connect in the console connects from AWS's own addresses, not from yours. To use it, set the SSH rule's source to the prefix list `com.amazonaws.us-east-1.ec2-instance-connect` instead of My IP.

## First boot

The first boot takes about 5 to 10 minutes. `user-data.sh` installs Docker and the Compose plugin, adds 2 GiB of swap, clones this repository to `/opt/sky-take-out`, and runs [deploy/aws/setup.sh](../deploy/aws/setup.sh), which:

- writes `.env` with new random database, Redis and JWT secrets, and turns on the simulated checkout
- starts MySQL, which creates the schema and demo menu from `docker/mysql/init`
- replaces the seeded merchant password `123456` with a random one before anything is reachable
- starts the backend and both frontends, and waits until all of them are healthy

Follow it after connecting to the instance:

```bash
sudo tail -f /var/log/cloud-init-output.log
```

When it prints `Sky Take-Out is running.`, open:

- Customer app: `http://<public IPv4 address>/`
- Merchant portal: `http://<public IPv4 address>:8081/`. The user is `admin`; the password is in the file below.

```bash
sudo cat /opt/sky-take-out/admin-password.txt
```

If the first boot stopped partway, fix the cause and run `sudo bash /opt/sky-take-out/deploy/aws/setup.sh`. It keeps whatever was already done.

## A stable address

The public IPv4 address changes whenever the instance is stopped and started. For a link that must not change, such as one on a résumé, allocate an Elastic IP and associate it with the instance. While attached to a running instance it costs the same $0.005 per hour as the address it replaces, but it is also billed while the instance is stopped, until you release it.

## Updating

```bash
cd /opt/sky-take-out
sudo git pull
sudo docker compose pull
sudo docker compose up -d
```

`.env`, the database and the merchant password are kept. A release that adds a schema file under `docker/mysql/init` needs that file applied once, as described in [docker/mysql/init/README.md](../docker/mysql/init/README.md).

Useful checks on the instance: `sudo docker compose ps`, `sudo docker compose logs -f backend`, `free -m`, and `sudo docker stats --no-stream`.

## Stopping and cleaning up

- **Stop** the instance when nobody needs the demo. The instance hours and the public IPv4 charge stop; the volume ($1.60 per month) and any Elastic IP keep billing.
- **Terminate** it to end all charges. The root volume and the data on it are deleted with it. Release the Elastic IP if you allocated one.

## Limitations of this setup

- **HTTP only.** There is no domain or TLS certificate, so browsers mark the pages "Not secure".
- **No backups.** Everything lives on one volume. An EBS snapshot is a cheap copy if the data matters.
- **Simulated payments**, as in the local demo: customers mark their own orders paid, and no money moves.
- **Public merchant portal.** Anyone with the password can change the menu and orders.
- **Image upload needs Aliyun OSS.** The demo menu's photos ship with the frontends, but a merchant who uploads a new dish photo needs Aliyun OSS credentials.
