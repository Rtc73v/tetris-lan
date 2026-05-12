const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const WebSocket = require('ws');

const PORT = Number(process.env.PORT || 8081);
const PUBLIC_DIR = path.join(__dirname, 'public');
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  let file = url.pathname === '/' ? '/index.html' : url.pathname;
  file = path.normalize(file).replace(/^\.\.(\/|\\|$)/, '');
  const abs = path.join(PUBLIC_DIR, file);
  if (!abs.startsWith(PUBLIC_DIR)) return send404(res);
  fs.readFile(abs, (err, data) => {
    if (err) return send404(res);
    res.writeHead(200, { 'Content-Type': MIME[path.extname(abs)] || 'application/octet-stream' });
    res.end(data);
  });
});
function send404(res) { res.writeHead(404); res.end('Not found'); }

const wss = new WebSocket.Server({ server });
const players = new Map();
let nextId = 1;

function cleanPlayer(p) {
  return { id: p.id, name: p.name, slot: p.slot, score: p.score, lines: p.lines, level: p.level, alive: p.alive, board: p.board, piece: p.piece };
}
function broadcast() {
  const payload = JSON.stringify({ type: 'state', players: [...players.values()].map(cleanPlayer), maxPlayers: 3 });
  for (const p of players.values()) if (p.ws.readyState === WebSocket.OPEN) p.ws.send(payload);
}
function send(ws, data) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data)); }
function freeSlot() {
  const used = new Set([...players.values()].map(p => p.slot));
  for (let i = 1; i <= 3; i++) if (!used.has(i)) return i;
  return null;
}

wss.on('connection', ws => {
  const slot = freeSlot();
  if (!slot) {
    send(ws, { type: 'full', message: '房间已满，最多3人' });
    ws.close();
    return;
  }
  const id = String(nextId++);
  const player = { id, slot, name: `玩家${slot}`, ws, score: 0, lines: 0, level: 1, alive: true, board: null, piece: null };
  players.set(id, player);
  send(ws, { type: 'welcome', id, slot, maxPlayers: 3 });
  broadcast();

  ws.on('message', raw => {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }
    const p = players.get(id);
    if (!p) return;
    if (msg.type === 'hello') p.name = String(msg.name || p.name).slice(0, 16);
    if (msg.type === 'update') {
      p.score = msg.score || 0;
      p.lines = msg.lines || 0;
      p.level = msg.level || 1;
      p.alive = msg.alive !== false;
      p.board = Array.isArray(msg.board) ? msg.board : null;
      p.piece = msg.piece || null;
    }
    if (msg.type === 'garbage') {
      for (const other of players.values()) {
        if (other.id !== id) send(other.ws, { type: 'garbage', from: id, lines: Math.min(4, Math.max(1, msg.lines | 0)) });
      }
    }
    if (msg.type === 'restart') {
      for (const other of players.values()) send(other.ws, { type: 'restart', from: id });
    }
    broadcast();
  });

  ws.on('close', () => { players.delete(id); broadcast(); });
});

server.listen(PORT, '0.0.0.0', () => {
  const ips = Object.values(os.networkInterfaces()).flat().filter(x => x && x.family === 'IPv4' && !x.internal).map(x => x.address);
  console.log(`Tetris LAN server running on port ${PORT}`);
  for (const ip of ips) console.log(`http://${ip}:${PORT}/`);
});
