#!/usr/bin/env bash
set -euo pipefail
# 用法: ./gen-selfsigned.sh <IP或域名>
# 示例: ./gen-selfsigned.sh 121.43.140.75
#       ./gen-selfsigned.sh tcm.example.com

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

CN="${1:?用法: $0 <IP或域名>}"

if [[ "$CN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  SAN="IP:${CN},DNS:localhost,IP:127.0.0.1"
else
  SAN="DNS:${CN},DNS:localhost,IP:127.0.0.1"
fi

openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout privkey.pem -out fullchain.pem \
  -subj "/CN=${CN}" \
  -addext "subjectAltName=${SAN}"

echo "已写入: ${HERE}/fullchain.pem 与 privkey.pem"
