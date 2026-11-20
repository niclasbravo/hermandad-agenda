#!/usr/bin/env bash
# Prepara un servidor Ubuntu 22.04/24.04 nuevo (AWS Lightsail o EC2) para correr el sistema.
# Se ejecuta UNA vez, en el servidor:  bash preparar-servidor.sh
set -euo pipefail

echo "== Docker y Docker Compose =="
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

echo "== Memoria de respaldo (swap de 2 GB) =="
# Compilar el backend (Maven) y correr Java + MySQL en 2 GB de RAM necesita swap.
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "== Zona horaria =="
sudo timedatectl set-timezone America/Santiago

mkdir -p ~/hermandad-agenda
echo
echo "Listo. Cierra la sesión SSH y vuelve a entrar para usar docker sin sudo."
