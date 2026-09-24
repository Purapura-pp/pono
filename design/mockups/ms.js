// The machine settings page of the redo (24–28, 31): the topics on the left, the machine it is
// along the top, the topic's form in the middle and, on the right, how to arrive at its values.
window.MS = (function () {
  var F = Frame, ic = F.ic;

  var TOPICS = [
    ['overview', '概况', 'info'], ['presets', '机型与预设', 'layers'], ['motion', '运动与轴', 'move'],
    ['nozzles', '吸嘴与吸嘴头', 'nozzle'], ['cameras', '相机', 'camera'], ['connection', '连接', 'power'],
    null, ['advanced', '高级 · 全部部件', 'tree']
  ];
  // What the collection on the calibration page found that is set here rather than calibrated.
  var HINTS = { nozzles: 2, cameras: 1, connection: 1 };

  var LUMEN = {
    name: 'LumenPnP v4.1', tag: '内置预设',
    sub: 'Opulo · 2 个吸嘴，双吸嘴取反 · 行程 433 × 487 mm · Marlin 2.1 · 顶部和底部相机',
    diff: '和预设相比改了 3 项'
  };

  function nav(active) {
    return '<div class="set-nav">' + TOPICS.map(function (t) {
      if (!t) return '<div class="sep"></div>';
      return '<div class="it' + (t[0] === active ? ' active' : '') + '">' + ic(t[2]) + t[1]
        + (HINTS[t[0]] ? '<span class="cnt">' + HINTS[t[0]] + '</span>' : '') + '</div>';
    }).join('') + '</div>';
  }

  function head(m) {
    m = m || LUMEN;
    return '<div class="ms-head"><div class="mi">' + ic('machine') + '</div><div class="grow" style="min-width:0">'
      + '<div class="nm">' + m.name + '<span class="tag">' + m.tag + '</span></div><div class="sub">' + m.sub + '</div></div>'
      + (m.diff ? '<span class="t2" style="font-size:12px">' + m.diff + '</span>' + F.btn('查看改动', null, 'ghost') : '')
      + F.btn('另存为预设', 'save') + '</div>';
  }

  function guide(html) {
    return '<aside class="guide"><div class="gh">' + ic('book', 'sm') + '通用方法</div>' + html + '</aside>';
  }

  // The bar under a topic's form: edits wait there until they are applied, as in every form.
  function foot(pending) {
    return '<span>' + (pending ? '<b style="color:var(--text)">' + pending + '</b>' : '没有未应用的改动') + '</span><span class="grow"></span>'
      + '<button class="btn sm' + (pending ? '' : ' disabled') + '">重置</button><button class="btn sm primary' + (pending ? '' : ' disabled') + '">应用</button>';
  }

  function app(o) {
    var center = '<section class="dock ms">' + nav(o.topic) + '<div class="ms-main">' + head(o.machine)
      + '<div class="ms-content"><div class="ms-form">' + o.body + '</div>' + (o.guide ? guide(o.guide) : '') + '</div>'
      + (o.foot !== undefined ? '<div class="ms-foot">' + foot(o.foot) + '</div>' : '') + '</div></section>';
    F.app({
      page: 'machine-settings', nav: F.NAV_REDO, centerClass: 'no-camera',
      top: o.top || { machine: 'off' },
      badges: { feeders: ['warn', 1], 'machine-settings': ['warn', 4], calibration: ['warn', 7] },
      center: center, status: o.status, overlay: o.overlay
    });
  }

  var svg = function (body, cls, box) {
    return '<svg class="' + (cls || '') + '" viewBox="' + (box || '0 0 150 80') + '" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">' + body + '</svg>';
  };

  // A pick and place seen from above: frame, the gantry, the head with its nozzles, the feeders
  // along the back and the bottom camera in front.
  function machinePic(nozzles) {
    var s = '<rect x="5" y="5" width="140" height="70" rx="5"/>'
      + '<line x1="14" y1="9" x2="14" y2="71"/><line x1="136" y1="9" x2="136" y2="71"/>';
    for (var i = 0; i < 9; i++) s += '<rect x="' + (24 + i * 11.5) + '" y="10" width="8" height="12" rx="1.5" opacity=".7"/>';
    s += '<rect x="10" y="33" width="130" height="9" rx="2"/>'
      + '<rect x="' + (nozzles > 1 ? 66 : 70) + '" y="29" width="' + (nozzles > 1 ? 22 : 14) + '" height="17" rx="3" fill="currentColor" fill-opacity=".22"/>';
    s += nozzles > 1 ? '<circle cx="72" cy="50" r="2.4"/><circle cx="82" cy="50" r="2.4"/>' : '<circle cx="77" cy="50" r="2.4"/>';
    s += '<rect x="26" y="55" width="62" height="14" rx="2" stroke-dasharray="3 2.5" opacity=".7"/><circle cx="106" cy="62" r="4"/><circle cx="106" cy="62" r="1.5"/>';
    return svg(s);
  }

  var NOZZLE_PICS = {
    single: '<rect x="20" y="6" width="12" height="18" rx="2"/><path d="M24 24v9h4v-9"/><path d="M26 33v4"/>'
      + '<path d="M42 10v22M38.5 13.5 42 10l3.5 3.5M38.5 28.5 42 32l3.5-3.5"/>',
    negated: '<circle cx="26" cy="7" r="4.5"/><path d="M21.5 7v11M30.5 7v5"/>'
      + '<rect x="16" y="18" width="11" height="13" rx="2"/><path d="M19.5 31v6h4v-6"/>'
      + '<rect x="25" y="12" width="11" height="13" rx="2"/><path d="M28.5 25v6h4v-6"/>'
      + '<path d="M8 14v14M5 25l3 3 3-3"/><path d="M44 28V14M41 17l3-3 3 3"/>',
    cam: '<circle cx="26" cy="9" r="6"/><circle cx="28.5" cy="7" r="1.6" fill="currentColor"/><path d="M20.5 12.5 18 18M31.5 12.5 34 15"/>'
      + '<rect x="12" y="18" width="11" height="13" rx="2"/><path d="M15.5 31v6h4v-6"/>'
      + '<rect x="29" y="15" width="11" height="13" rx="2"/><path d="M32.5 28v6h4v-6"/>'
      + '<path d="M6 16v14M3 27l3 3 3-3"/><path d="M47 28V14M44 17l3-3 3 3"/>',
    independent: '<rect x="10" y="10" width="11" height="15" rx="2"/><path d="M13.5 25v7h4v-7"/>'
      + '<rect x="31" y="10" width="11" height="15" rx="2"/><path d="M34.5 25v7h4v-7"/>'
      + '<path d="M26 4v34M23 7l3-3 3 3M23 35l3 3 3-3" opacity=".55"/><path d="M4 12v16M1.5 14.5 4 12l2.5 2.5M1.5 25.5 4 28l2.5-2.5"/><path d="M48 12v16M45.5 14.5 48 12l2.5 2.5M45.5 25.5 48 28l2.5-2.5"/>'
  };

  function nozzlePic(kind) {
    return svg(NOZZLE_PICS[kind], 'pic', '0 0 52 42');
  }

  return { app: app, nav: nav, head: head, guide: guide, foot: foot, machinePic: machinePic, nozzlePic: nozzlePic, LUMEN: LUMEN };
})();
