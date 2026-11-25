#!/usr/bin/env bash
# Launches the Sky Take-Out demo on one new EC2 instance. Run it in AWS CloudShell, which is
# already signed in and uses the region chosen in the console:
#
#   curl -fsSLO https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main/deploy/aws/launch.sh
#   bash launch.sh
#
# It creates a security group (ports 80 and 8081 open to everyone, SSH only from EC2 Instance
# Connect) and one t3.micro whose first boot runs deploy/aws/user-data.sh. If an instance named
# sky-take-out already exists, it stops instead of starting a second one.
set -euo pipefail

NAME=sky-take-out
INSTANCE_TYPE=${INSTANCE_TYPE:-t3.micro}
RAW=${RAW:-https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main}
REGION=${AWS_REGION:-${AWS_DEFAULT_REGION:-$(aws configure get region || true)}}
if [ -z "$REGION" ]; then
  echo "No region is set. Run it as: AWS_REGION=us-east-1 bash launch.sh" >&2
  exit 1
fi
export AWS_DEFAULT_REGION=$REGION AWS_PAGER=""

text() { aws "$@" --output text; }

existing=$(text ec2 describe-instances --filters "Name=tag:Name,Values=$NAME" \
  "Name=instance-state-name,Values=pending,running,stopping,stopped" --query 'Reservations[].Instances[].InstanceId')
if [ -n "$existing" ]; then
  echo "An instance named $NAME already exists in $REGION: $existing" >&2
  exit 1
fi

vpc=$(text ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId')
if [ "$vpc" = None ]; then
  echo "$REGION has no default VPC, which this script launches into." >&2
  exit 1
fi

# A default subnet in a zone that offers the instance type: not every zone has every type.
subnet=None
for zone in $(text ec2 describe-instance-type-offerings --location-type availability-zone \
    --filters "Name=instance-type,Values=$INSTANCE_TYPE" --query 'InstanceTypeOfferings[].Location'); do
  subnet=$(text ec2 describe-subnets --filters "Name=vpc-id,Values=$vpc" "Name=availability-zone,Values=$zone" \
    Name=default-for-az,Values=true --query 'Subnets[0].SubnetId')
  [ "$subnet" != None ] && break
done
if [ "$subnet" = None ]; then
  echo "No default subnet in $REGION offers $INSTANCE_TYPE." >&2
  exit 1
fi

sg=$(text ec2 describe-security-groups --filters "Name=vpc-id,Values=$vpc" "Name=group-name,Values=$NAME" \
  --query 'SecurityGroups[0].GroupId')
ami=$(text ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value)
user_data=$(mktemp)
curl -fsSL "$RAW/deploy/aws/user-data.sh" -o "$user_data"

# Everything above only reads. Ask before creating anything.
echo "Ready to launch one $INSTANCE_TYPE with a 20 GiB gp3 volume in $REGION, from $ami."
[ "$sg" = None ] && echo "It also creates the security group $NAME."
echo "In us-east-1 that costs about \$12.84 a month with its public IPv4 address."
read -r -p "Launch it now? [y/N] " answer
if [ "$answer" != y ] && [ "$answer" != Y ]; then
  echo "Nothing launched."
  exit 0
fi

if [ "$sg" = None ]; then
  sg=$(text ec2 create-security-group --group-name "$NAME" --vpc-id "$vpc" \
    --description "Sky Take-Out demo: customer app, merchant portal, EC2 Instance Connect" --query GroupId)
  rules='{"IpProtocol":"tcp","FromPort":80,"ToPort":80,"IpRanges":[{"CidrIp":"0.0.0.0/0","Description":"Customer app"}]}'
  rules+=',{"IpProtocol":"tcp","FromPort":8081,"ToPort":8081,"IpRanges":[{"CidrIp":"0.0.0.0/0","Description":"Merchant portal"}]}'
  connect=$(text ec2 describe-managed-prefix-lists \
    --filters "Name=prefix-list-name,Values=com.amazonaws.$REGION.ec2-instance-connect" --query 'PrefixLists[0].PrefixListId')
  if [ "$connect" != None ]; then
    rules+=',{"IpProtocol":"tcp","FromPort":22,"ToPort":22,"PrefixListIds":[{"PrefixListId":"'"$connect"'","Description":"EC2 Instance Connect"}]}'
  fi
  aws ec2 authorize-security-group-ingress --group-id "$sg" --ip-permissions "[$rules]" >/dev/null
  echo "Created security group $sg."
fi

id=$(text ec2 run-instances --image-id "$ami" --instance-type "$INSTANCE_TYPE" --subnet-id "$subnet" \
  --security-group-ids "$sg" --user-data "file://$user_data" \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":20,"VolumeType":"gp3","DeleteOnTermination":true}}]' \
  --metadata-options HttpTokens=required,HttpEndpoint=enabled \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$NAME}]" "ResourceType=volume,Tags=[{Key=Name,Value=$NAME}]" \
  --query 'Instances[0].InstanceId')
echo "Launched $id. Waiting until it is running..."
aws ec2 wait instance-running --instance-ids "$id"
ip=$(text ec2 describe-instances --instance-ids "$id" --query 'Reservations[0].Instances[0].PublicIpAddress')

cat <<EOF

$id is running at $ip. Its first boot takes about 5 to 10 minutes; then open:
  Customer app:    http://$ip/
  Merchant portal: http://$ip:8081/  (user admin)

To follow the first boot and read the merchant password, open the EC2 console, select the
instance, choose Connect, then EC2 Instance Connect, and run:
  sudo tail -f /var/log/cloud-init-output.log
  sudo cat /opt/sky-take-out/admin-password.txt
EOF
