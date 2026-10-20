#!/usr/bin/env bash
# Run only after the owner approves the new restricted deployment account and keys.
set -euo pipefail
backend_key=${1:?path to approved backend PUBLIC key}
frontend_key=${2:?path to approved frontend PUBLIC key}
[[ $(id -u) -eq 0 ]] || exit 77
for file in "$backend_key" "$frontend_key"; do
  grep -q '^ssh-ed25519 ' "$file" || { echo 'Expected an Ed25519 public key'; exit 64; }
done
if ! id deploy-grainettable >/dev/null 2>&1; then
  useradd --create-home --shell /bin/sh deploy-grainettable
  passwd -l deploy-grainettable >/dev/null
fi
chown root:root /home/deploy-grainettable
chmod 755 /home/deploy-grainettable
install -d -o root -g root -m 755 /home/deploy-grainettable/.ssh
install -o root -g root -m 755 /opt/grainettable/repos/backend/deploy/grainettable-ci.sh /usr/local/sbin/grainettable-ci
{
  printf 'restrict,command="/usr/bin/sudo -n /usr/local/sbin/grainettable-ci backend \\"$SSH_ORIGINAL_COMMAND\\"" %s\n' "$(cat "$backend_key")"
  printf 'restrict,command="/usr/bin/sudo -n /usr/local/sbin/grainettable-ci frontend \\"$SSH_ORIGINAL_COMMAND\\"" %s\n' "$(cat "$frontend_key")"
} > /home/deploy-grainettable/.ssh/authorized_keys
chmod 644 /home/deploy-grainettable/.ssh/authorized_keys
printf '%s\n' 'deploy-grainettable ALL=(root) NOPASSWD: /usr/local/sbin/grainettable-ci backend *, /usr/local/sbin/grainettable-ci frontend *' > /etc/sudoers.d/grainettable-ci
chmod 440 /etc/sudoers.d/grainettable-ci
visudo -cf /etc/sudoers.d/grainettable-ci
echo 'Two restricted deployment keys installed. No root authorized_keys entry added.'
