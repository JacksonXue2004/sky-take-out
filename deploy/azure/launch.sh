#!/usr/bin/env bash
# Launches the Sky Take-Out demo on one Azure VM. Run it in Azure Cloud Shell (Bash), which is
# already signed in:
#
#   curl -fsSLO https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main/deploy/azure/launch.sh
#   bash launch.sh
#
# It creates the resource group sky-take-out with one Ubuntu 24.04 VM, of a size the 12-month free
# services cover, whose first boot runs deploy/first-boot.sh, and opens ports 80 and 8081. No SSH
# port is opened; the commands printed at the end read the VM's log and password instead.
set -euo pipefail

GROUP=${GROUP:-sky-take-out}
NAME=sky-take-out
SIZES=${SIZES:-Standard_B2ats_v2 Standard_B1s}
IMAGE=Canonical:ubuntu-24_04-lts:server:latest
RAW=${RAW:-https://raw.githubusercontent.com/JacksonXue2004/sky-take-out/main}

if az vm show --resource-group "$GROUP" --name "$NAME" --output none 2>/dev/null; then
  echo "A VM named $NAME already exists in resource group $GROUP." >&2
  exit 1
fi
subscription=$(az account show --query name --output tsv | tr -d '\r')

# Azure for Students subscriptions may deploy only to a few regions, set by a policy.
allowed=$(az policy assignment list --query "[].parameters.listOfAllowedLocations.value[]" \
  --output tsv 2>/dev/null | tr -d '\r' | sort -u | tr '\n' ' ') || true
regions=${LOCATION:-${allowed:-eastus2 westus2 centralus eastus westus3}}

# The first region, in that order, where one of the sizes (in order of preference) is offered.
location="" size=""
for region in $regions; do
  offered=" $(az vm list-skus --location "$region" --resource-type virtualMachines \
    --query "[?length(restrictions[?type=='Location']) == \`0\`].name" --output tsv 2>/dev/null | tr -d '\r' | tr '\n' ' ') "
  for candidate in $SIZES; do
    if [[ $offered == *" $candidate "* ]]; then
      location=$region size=$candidate
      break 2
    fi
  done
done
if [ -z "$location" ]; then
  echo "None of $SIZES is offered to this subscription in: $regions" >&2
  echo "Pick a region yourself and run: LOCATION=<region> bash launch.sh" >&2
  exit 1
fi

custom_data=$(mktemp)
curl -fsSL "$RAW/deploy/first-boot.sh" -o "$custom_data"

# Everything above only reads. Ask before creating anything.
echo "Subscription: $subscription"
echo "Ready to create, in $location: resource group $GROUP, VM $NAME ($size, Ubuntu 24.04) with a"
echo "64 GiB Premium SSD, a static public IP, and ports 80 and 8081 open."
echo "Azure for Students covers $size hours and two 64 GiB SSDs among its 12-month free services;"
echo "the public IP, about \$3.65 a month, comes out of the \$100 credit."
read -r -p "Create it now? [y/N] " answer
if [ "$answer" != y ] && [ "$answer" != Y ]; then
  echo "Nothing created."
  exit 0
fi

az group create --name "$GROUP" --location "$location" --output none
az vm create --resource-group "$GROUP" --name "$NAME" --location "$location" --size "$size" \
  --image "$IMAGE" --admin-username azureuser --generate-ssh-keys \
  --custom-data "$custom_data" --public-ip-sku Standard --nsg-rule NONE \
  --os-disk-size-gb 64 --storage-sku Premium_LRS --output none
az vm open-port --resource-group "$GROUP" --name "$NAME" --port 80 --priority 1010 --output none
az vm open-port --resource-group "$GROUP" --name "$NAME" --port 8081 --priority 1020 --output none
ip=$(az vm show --show-details --resource-group "$GROUP" --name "$NAME" --query publicIps --output tsv | tr -d '\r')

cat <<EOF

$NAME is running at $ip. Its first boot takes about 5 to 10 minutes; then open:
  Customer app:    http://$ip/
  Merchant portal: http://$ip:8081/  (user admin)

Follow the first boot, and read the merchant password once it has finished:
  az vm run-command invoke -g $GROUP -n $NAME --command-id RunShellScript --query "value[0].message" -o tsv --scripts "tail -n 25 /var/log/cloud-init-output.log"
  az vm run-command invoke -g $GROUP -n $NAME --command-id RunShellScript --query "value[0].message" -o tsv --scripts "cat /opt/sky-take-out/admin-password.txt"

To delete everything this script created: az group delete --name $GROUP
EOF
