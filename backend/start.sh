#!/data/data/com.termux/files/usr/bin/bash
# AppRyous Backend — start script untuk Termux
# Usage: ./start.sh [--port 8080] [--sdcard /storage/XXXX-XXXX]

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --port)    export PORT="$2"; shift 2 ;;
        --sdcard)  export SDCARD_ROOT="$2"; shift 2 ;;
        --debug)   export DEBUG=1; shift ;;
        *) shift ;;
    esac
done

echo "=== AppRyous Backend ==="
echo "Python: $(python3 --version)"
echo "Dir: $SCRIPT_DIR"

# Install deps jika belum
if ! python3 -c "import flask" 2>/dev/null; then
    echo "Installing dependencies..."
    pip3 install -r requirements.txt
fi

# Jalankan server
python3 server.py
