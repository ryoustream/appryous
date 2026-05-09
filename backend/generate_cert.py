"""
generate_cert.py — Self-signed SSL cert via openssl CLI
Tidak butuh library Python apapun — pakai openssl bawaan Termux.

Install openssl di Termux jika belum:
  pkg install openssl
"""
import os, sys, subprocess, socket

_here = os.path.dirname(os.path.abspath(__file__))
CERT  = os.path.join(_here, "cert.pem")
KEY   = os.path.join(_here, "key.pem")

# Detect local IP
try:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("8.8.8.8", 80))
    LOCAL_IP = s.getsockname()[0]
    s.close()
except Exception:
    LOCAL_IP = "127.0.0.1"

def _openssl_available():
    try:
        subprocess.run(["openssl", "version"],
            capture_output=True, check=True)
        return True
    except (FileNotFoundError, subprocess.CalledProcessError):
        return False

def generate():
    if not _openssl_available():
        print("⚠️  openssl tidak ditemukan.")
        print("   Install: pkg install openssl")
        print("   Server tetap jalan via HTTP (tunnel tetap HTTPS).")
        return False

    # SAN config — IP lokal + localhost
    san_cfg = os.path.join(_here, "_san.cnf")
    with open(san_cfg, "w") as f:
        f.write(f"""[req]
distinguished_name = dn
x509_extensions    = v3_req
prompt             = no

[dn]
C  = ID
O  = RYOU Media Server
CN = {LOCAL_IP}

[v3_req]
subjectAltName = IP:{LOCAL_IP}, IP:127.0.0.1
""")

    try:
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "rsa:2048",
            "-keyout", KEY, "-out", CERT,
            "-days", "3650",
            "-nodes",
            "-config", san_cfg,
        ], check=True, capture_output=True)

        os.remove(san_cfg)
        print(f"✅ cert.pem + key.pem dibuat untuk IP: {LOCAL_IP}")
        print("   Restart server.py untuk aktifkan HTTPS.")
        return True

    except subprocess.CalledProcessError as e:
        print(f"❌ Gagal generate cert: {e.stderr.decode()[:200]}")
        try: os.remove(san_cfg)
        except: pass
        return False

if __name__ == "__main__":
    generate()
