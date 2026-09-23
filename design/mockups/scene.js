// Fake camera scenes for the mockups (SVG strings).
window.Scene = (function () {
  function reticle(w, h, opts) {
    opts = opts || {};
    var cx = w / 2, cy = h / 2, c = opts.color || '#3dff8a';
    var s = '<g opacity="0.9" stroke="' + c + '" fill="none" stroke-width="1.2">';
    s += '<line x1="0" y1="' + cy + '" x2="' + w + '" y2="' + cy + '" opacity="0.55"/>';
    s += '<line x1="' + cx + '" y1="0" x2="' + cx + '" y2="' + h + '" opacity="0.55"/>';
    s += '<circle cx="' + cx + '" cy="' + cy + '" r="42"/>';
    s += '<circle cx="' + cx + '" cy="' + cy + '" r="6"/>';
    for (var i = 1; i <= 6; i++) {
      var d = i * 60, t = (i % 3 === 0) ? 12 : 6;
      s += '<line x1="' + (cx + d) + '" y1="' + (cy - t) + '" x2="' + (cx + d) + '" y2="' + (cy + t) + '"/>';
      s += '<line x1="' + (cx - d) + '" y1="' + (cy - t) + '" x2="' + (cx - d) + '" y2="' + (cy + t) + '"/>';
      s += '<line x1="' + (cx - t) + '" y1="' + (cy + d) + '" x2="' + (cx + t) + '" y2="' + (cy + d) + '"/>';
      s += '<line x1="' + (cx - t) + '" y1="' + (cy - d) + '" x2="' + (cx + t) + '" y2="' + (cy - d) + '"/>';
    }
    s += '</g>';
    if (opts.footprint) {
      // 0603 footprint outline centered on the reticle.
      s += '<g stroke="#ffd34d" fill="none" stroke-width="1.5" stroke-dasharray="6 4" opacity="0.95">';
      s += '<rect x="' + (cx - 66) + '" y="' + (cy - 36) + '" width="132" height="72" rx="3"/>';
      s += '<rect x="' + (cx - 62) + '" y="' + (cy - 30) + '" width="40" height="60"/>';
      s += '<rect x="' + (cx + 22) + '" y="' + (cy - 30) + '" width="40" height="60"/>';
      s += '</g>';
      s += '<text x="' + (cx + 74) + '" y="' + (cy - 42) + '" fill="#ffd34d" font-family="Segoe UI, sans-serif" font-size="14" font-weight="600">' + (opts.footprintLabel || 'R12 · 0603') + '</text>';
    }
    return s;
  }

  function pad(x, y, w, h, tin) {
    var f = tin ? 'url(#tin)' : 'url(#cu)';
    return '<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + h + '" rx="3" fill="' + f + '" stroke="#0a2b22" stroke-width="1"/>';
  }
  function res(x, y, label, rot) {
    var s = '<g transform="translate(' + x + ' ' + y + ') rotate(' + (rot || 0) + ')">';
    s += '<rect x="-60" y="-30" width="120" height="60" rx="3" fill="#1c1c1e" stroke="#000" stroke-width="1"/>';
    s += '<rect x="-60" y="-30" width="22" height="60" rx="2" fill="#b9bfc6"/>';
    s += '<rect x="38" y="-30" width="22" height="60" rx="2" fill="#b9bfc6"/>';
    s += '<text x="0" y="6" text-anchor="middle" fill="#e6e6e6" font-family="Arial" font-size="18" font-weight="700">' + label + '</text>';
    s += '</g>';
    return s;
  }
  function cap(x, y, rot) {
    var s = '<g transform="translate(' + x + ' ' + y + ') rotate(' + (rot || 0) + ')">';
    s += '<rect x="-52" y="-28" width="104" height="56" rx="4" fill="#8a6a44" stroke="#3a2a15" stroke-width="1"/>';
    s += '<rect x="-52" y="-28" width="20" height="56" rx="2" fill="#c9ccd0"/>';
    s += '<rect x="32" y="-28" width="20" height="56" rx="2" fill="#c9ccd0"/>';
    s += '</g>';
    return s;
  }
  function silk(x, y, txt, size) {
    return '<text x="' + x + '" y="' + y + '" fill="#e9e6d6" font-family="Arial" font-size="' + (size || 20) + '" font-weight="700" opacity="0.92">' + txt + '</text>';
  }

  function pcb(opts) {
    opts = opts || {};
    var w = 1200, h = 760;
    var s = '<svg viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">';
    s += '<defs>';
    s += '<linearGradient id="tin" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e8ecef"/><stop offset="1" stop-color="#aab2ba"/></linearGradient>';
    s += '<linearGradient id="cu" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e0b26a"/><stop offset="1" stop-color="#a9772f"/></linearGradient>';
    s += '<radialGradient id="vig" cx="0.5" cy="0.5" r="0.75"><stop offset="0.55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.55"/></radialGradient>';
    s += '<pattern id="fr4" width="6" height="6" patternUnits="userSpaceOnUse"><rect width="6" height="6" fill="#0d4a38"/><rect width="3" height="3" fill="#0b4433" opacity="0.7"/></pattern>';
    s += '</defs>';
    s += '<rect width="' + w + '" height="' + h + '" fill="url(#fr4)"/>';
    // traces
    s += '<g stroke="#146b50" stroke-width="7" fill="none" stroke-linecap="round" opacity="0.95">';
    s += '<path d="M120 120 H 380 V 300 H 520"/><path d="M900 80 V 260 H 760"/><path d="M200 640 H 460 V 520"/><path d="M1040 700 V 520 H 880"/><path d="M640 120 V 60"/>';
    s += '</g>';
    // ground plane hint
    s += '<rect x="40" y="380" width="260" height="200" fill="#0f5a43" opacity="0.5" rx="4"/>';
    // vias
    s += '<g>';
    [[380, 300], [760, 260], [460, 520], [880, 520], [150, 700], [1100, 150]].forEach(function (p) {
      s += '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="12" fill="url(#cu)"/><circle cx="' + p[0] + '" cy="' + p[1] + '" r="5" fill="#0a2b22"/>';
    });
    s += '</g>';
    // Fiducial
    s += '<circle cx="1000" cy="160" r="34" fill="#0d4a38" stroke="#146b50" stroke-width="2"/><circle cx="1000" cy="160" r="18" fill="url(#cu)"/>';
    s += silk(1046, 168, 'FID1', 22);
    // Placed parts
    s += pad(150, 110, 44, 66, true); s += pad(266, 110, 44, 66, true); s += res(230, 143, '103', 0); s += silk(180, 92, 'R11', 20);
    s += pad(600, 560, 66, 44, true); s += pad(600, 680, 66, 44, true); s += cap(633, 642, 90); s += silk(690, 630, 'C14', 20);
    s += pad(820, 600, 40, 60, true); s += pad(930, 600, 40, 60, true); s += res(895, 630, '4R7', 0); s += silk(860, 585, 'R13', 20);
    // IC footprint (empty) top-left of center
    s += '<g>';
    for (var i = 0; i < 8; i++) { s += pad(330 + i * 34, 380, 20, 46, true); s += pad(330 + i * 34, 520, 20, 46, true); }
    s += '<rect x="322" y="432" width="286" height="82" fill="none" stroke="#e9e6d6" stroke-width="2.5" opacity="0.9"/>';
    s += '<circle cx="338" cy="448" r="5" fill="#e9e6d6"/>';
    s += silk(340, 372, 'U3', 20);
    s += '</g>';
    // Target pads (empty 0603 for R12) at center
    var cx = w / 2, cy = h / 2;
    s += pad(cx - 62, cy - 30, 40, 60, true); s += pad(cx + 22, cy - 30, 40, 60, true);
    s += silk(cx - 22, cy - 44, 'R12', 20);
    s += '<rect width="' + w + '" height="' + h + '" fill="url(#vig)"/>';
    if (opts.reticle !== false) s += reticle(w, h, { footprint: opts.footprint !== false, footprintLabel: opts.footprintLabel });
    s += '</svg>';
    return s;
  }

  function tape(opts) {
    opts = opts || {};
    var w = 1200, h = 760;
    var s = '<svg viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">';
    s += '<defs>';
    s += '<linearGradient id="alu" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3f46"/><stop offset="0.5" stop-color="#2a2f36"/><stop offset="1" stop-color="#1f242b"/></linearGradient>';
    s += '<linearGradient id="tapeg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1a1a1c"/><stop offset="1" stop-color="#0f0f11"/></linearGradient>';
    s += '<radialGradient id="vig2" cx="0.5" cy="0.5" r="0.75"><stop offset="0.5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.6"/></radialGradient>';
    s += '</defs>';
    s += '<rect width="' + w + '" height="' + h + '" fill="url(#alu)"/>';
    // brushed lines
    s += '<g stroke="#4a5058" stroke-width="1" opacity="0.35">';
    for (var y = 0; y < h; y += 9) s += '<line x1="0" y1="' + y + '" x2="' + w + '" y2="' + (y + 3) + '"/>';
    s += '</g>';
    // tape strip
    var ty = 332, th = 330; // sprocket hole row sits on the reticle centre (y = 380)
    s += '<rect x="0" y="' + ty + '" width="' + w + '" height="' + th + '" fill="url(#tapeg)" stroke="#000" stroke-width="2"/>';
    // sprocket holes (one of them at x = 600, the view centre)
    for (var x = 120; x < w; x += 160) s += '<circle cx="' + x + '" cy="' + (ty + 48) + '" r="24" fill="#5c6269" stroke="#0a0a0a" stroke-width="2"/>';
    // pockets with parts (every 160 = 4mm pitch)
    for (var px = 200, i = 0; px < w; px += 160, i++) {
      s += '<rect x="' + (px - 58) + '" y="' + (ty + 130) + '" width="116" height="150" rx="6" fill="#0a0a0c" stroke="#3a3a40" stroke-width="2"/>';
      if (i < 2) continue; // already picked pockets are empty
      s += '<g transform="translate(' + px + ' ' + (ty + 205) + ')">';
      s += '<rect x="-46" y="-24" width="92" height="48" rx="3" fill="#1c1c1e" stroke="#000"/>';
      s += '<rect x="-46" y="-24" width="17" height="48" fill="#b9bfc6"/><rect x="29" y="-24" width="17" height="48" fill="#b9bfc6"/>';
      s += '<text x="0" y="6" text-anchor="middle" fill="#e6e6e6" font-family="Arial" font-size="15" font-weight="700">104</text>';
      s += '</g>';
    }
    // cover tape edge (transparent film glimmer)
    s += '<rect x="0" y="' + (ty + 100) + '" width="' + w + '" height="2" fill="#7c8590" opacity="0.5"/>';
    s += '<rect width="' + w + '" height="' + h + '" fill="url(#vig2)"/>';
    if (opts.reticle !== false) {
      s += reticle(w, h, { color: '#3dff8a' });
      // hole detection marker for first sprocket hole in view
      s += '<g stroke="#ffd34d" fill="none" stroke-width="2"><circle cx="600" cy="' + (ty + 48) + '" r="32" stroke-dasharray="5 4"/></g>';
      s += '<text x="644" y="' + (ty + 30) + '" fill="#ffd34d" font-family="Segoe UI, sans-serif" font-size="14" font-weight="600">参考孔 1 · 已识别 · 偏差 0.02 mm</text>';
    }
    s += '</svg>';
    return s;
  }

  return { pcb: pcb, tape: tape };
})();
