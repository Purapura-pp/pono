// The frame every page of the redesign shares: top bar, navigation rail, status bar, the dock and
// the properties column, as the shell draws them after the redo (P4). A page supplies only its
// own centre and properties; see 07-parts.html for the pattern.
window.Frame = (function () {
  function ic(name, cls) {
    return '<svg class="i' + (cls ? ' ' + cls : '') + '"><use href="#i-' + name + '"/></svg>';
  }

  var NAV = [
    ['job', '任务', 'job'], ['feeders', '飞达', 'feeder'], ['parts', '元件', 'parts'],
    ['packages', '封装', 'pkg'], ['boards', '单板', 'board'], ['panels', '拼板', 'layers'],
    ['vision', '视觉', 'eye'], null,
    ['machine', '机器', 'machine'], ['issues', '问题', 'alert'], ['calibration', '校准', 'target'],
    ['log', '日志', 'log']
  ];

  function machineChip(state) {
    if (state === 'off') {
      return '<div class="chip err"><span class="led"></span>已断开</div><button class="btn sm primary">' + ic('power', 'sm') + '连接</button>';
    }
    if (state === 'enabled') return '<div class="chip warn"><span class="led"></span>已启用 · 未归位</div>';
    if (state === 'busy') return '<div class="chip accent"><span class="led" style="background:var(--accent)"></span>运行中</div>';
    return '<div class="chip ok"><span class="led"></span>已启用 · 已归位</div>';
  }

  function topbar(o) {
    o = o || {};
    var off = o.machine === 'off';
    var s = '<header class="topbar">';
    s += '<div class="brand"><span class="logo">P</span>Pono <span class="ver">2.6</span></div>';
    s += '<nav class="menus"><span>文件</span><span>编辑</span><span>视图</span><span>机器</span><span>脚本</span><span>帮助</span></nav>';
    s += '<div class="divider-v"></div>';
    s += '<div class="jobname">' + ic('folder', 'sm') + '<b>' + (o.job || 'demo-board.job.xml') + '</b>' + (o.jobDirty ? '<span class="dot" title="任务未保存"></span>' : '') + '</div>';
    if (o.configDirty) s += '<div class="chip warn" title="点击立即保存"><span class="led"></span>配置未保存</div>';
    s += '<div class="grow"></div>';
    s += machineChip(o.machine);
    s += '<div class="divider-v"></div>';
    s += '<div class="row" style="gap:6px">';
    s += '<button class="btn primary ok' + (off ? ' disabled' : '') + '">' + ic('play') + '启动</button>';
    s += '<button class="btn disabled">' + ic('pause') + '暂停</button>';
    s += '<button class="btn' + (off ? ' disabled' : '') + '">' + ic('step') + '单步</button>';
    s += '<button class="btn danger disabled">' + ic('stop') + '终止</button>';
    s += '</div>';
    s += '<button class="btn solid-danger' + (off ? ' disabled' : '') + '" title="Ctrl+Shift+X">' + ic('power') + '停止机器</button>';
    s += '<div class="progress" style="width:128px;min-width:0"><span class="t2 mono" style="font-size:12px">0 / 48</span><div class="bar"><i style="width:0%"></i></div><span class="muted" style="font-size:12px">就绪</span></div>';
    s += '<div class="divider-v"></div>';
    s += '<div class="search" style="width:150px">' + ic('search', 'sm') + '搜索…<kbd>Ctrl K</kbd></div>';
    s += '<button class="btn icon ghost" title="通知">' + ic('bell') + '</button>';
    s += '<button class="btn icon ghost" title="主题">' + ic(document.documentElement.dataset.theme === 'light' ? 'sun' : 'moon') + '</button>';
    return s + '</header>';
  }

  function rail(active, badges) {
    badges = badges || {};
    var s = '<aside class="rail">';
    NAV.forEach(function (n) {
      if (!n) {
        s += '<div class="sep"></div>';
        return;
      }
      var b = badges[n[0]];
      s += '<div class="item' + (n[0] === active ? ' active' : '') + '">' + ic(n[2]) + n[1]
        + (b ? '<span class="badge' + (b[0] === 'warn' ? ' warn' : '') + '">' + b[1] + '</span>' : '') + '</div>';
    });
    s += '<div class="spacer"></div><div class="item' + (active === 'settings' ? ' active' : '') + '">' + ic('gear') + '设置</div>';
    return s + '</aside>';
  }

  function status(o) {
    o = o || {};
    var s = '<footer class="statusbar">';
    s += '<div class="item">' + (o.chip || '<span class="status ok" style="height:18px">就绪</span>') + '<span>' + (o.text || '') + '</span></div>';
    (o.extra || []).forEach(function (e) {
      s += '<div class="sep"></div><div class="item muted">' + e + '</div>';
    });
    s += '<div class="right">';
    (o.right || []).forEach(function (r) {
      s += '<div class="item">' + r + '</div>';
    });
    s += '<div class="sep"></div><div class="item muted">mm</div><div class="item muted">v2.6-pono.1</div></div>';
    return s + '</footer>';
  }

  function dro(values, compact) {
    var axes = ['X', 'Y', 'Z', 'C'];
    return '<div class="glass dro' + (compact ? ' compact' : '') + '">' + values.map(function (v, i) {
      return '<div class="ax"><span class="k">' + axes[i] + '</span><span class="v">' + v + '<span class="u">' + (i === 3 ? '°' : 'mm') + '</span></span></div>';
    }).join('') + '</div>';
  }

  // The camera as a strip: pages that do not work with it keep it small, and it can be dragged back.
  function strip(o) {
    o = o || {};
    return '<section class="camera"><div class="view" id="cam"></div>'
      + '<div class="ov tl"><div class="glass pills"><span class="pill active">' + ic('camera', 'sm') + (o.camera || 'Top · 头 H1') + '</span><span class="pill">Bottom</span></div></div>'
      + '<div class="ov tr"><div class="glass pills"><span class="pill">大</span><span class="pill on">小窗</span><span class="pill">隐藏</span></div></div>'
      + '<div class="ov bl">' + dro(o.dro || ['120.450', '85.210', '-2.000', '90.00'], true) + '</div>'
      + '<div class="ov bc"><div class="glass handle">' + ic('grip') + '拖动以放大相机</div></div>'
      + '</section>';
  }

  // The camera at full size, with the tools the job page has.
  function camera(o) {
    o = o || {};
    return '<section class="camera"><div class="view" id="cam"></div>'
      + '<div class="ov tl"><div class="glass pills"><span class="pill' + (o.bottom ? '' : ' active') + '">' + ic('camera', 'sm') + 'Top · 头 H1</span><span class="pill' + (o.bottom ? ' active' : '') + '">Bottom</span><span class="pill">' + ic('panel', 'sm') + '并排</span></div>'
      + '<div class="glass chip neutral" style="height:32px;border-radius:10px"><span class="mono">1 px = ' + (o.upp || '0.0209') + ' mm</span></div></div>'
      + '<div class="ov tr"><div class="glass pills icons"><span class="pill on" title="十字线">' + ic('crosshair') + '</span><span class="pill" title="网格">' + ic('grid') + '</span><span class="pill" title="标尺">' + ic('ruler') + '</span><span class="pill on" title="封装轮廓">' + ic('footprint') + '</span></div>'
      + '<div class="glass pills"><span class="pill" title="补光灯">' + ic('zap', 'sm') + '</span><span class="pill">' + ic('search', 'sm') + '100%</span><span class="pill" title="截图">' + ic('capture', 'sm') + '</span><span class="pill" title="全屏">' + ic('maximize', 'sm') + '</span></div></div>'
      + (o.instr || '')
      + '<div class="ov bl">' + dro(o.dro || ['120.450', '85.210', '-2.000', '90.00']) + '</div>'
      + '<div class="ov br"><button class="btn glass" style="height:38px;border-radius:10px">' + ic('move') + '手动控制<kbd>Ctrl Shift J</kbd></button></div>'
      + '</section>';
  }

  function table(cols, rows, sel) {
    var s = '<table class="grid"><thead><tr>';
    cols.forEach(function (c) {
      s += '<th' + (c.r ? ' class="r"' : '') + (c.w ? ' style="width:' + c.w + 'px"' : '') + '>' + c.t + '</th>';
    });
    s += '</tr></thead><tbody>';
    rows.forEach(function (row, i) {
      if (typeof row === 'string') {
        s += '<tr class="grp"><td colspan="' + cols.length + '">' + row + '</td></tr>';
        return;
      }
      s += '<tr' + (i === sel ? ' class="sel"' : '') + '>';
      row.forEach(function (cell, j) {
        var c = cols[j] || {};
        var cls = [c.cls || '', c.r ? 'r' : ''].join(' ').trim();
        s += '<td' + (cls ? ' class="' + cls + '"' : '') + '>' + cell + '</td>';
      });
      s += '</tr>';
    });
    return s + '</tbody></table>';
  }

  function btn(label, icon, cls, extra) {
    return '<button class="btn sm' + (cls ? ' ' + cls : '') + '"' + (extra || '') + '>' + (icon ? ic(icon, 'sm') : '') + (label || '') + '</button>';
  }

  function sep() {
    return '<span class="sep"></span>';
  }

  function filter(text) {
    return '<div class="filter">' + ic('search', 'sm') + text + '<kbd style="margin-left:auto">/</kbd></div>';
  }

  function seg(items, active, tight) {
    return '<div class="seg' + (tight ? ' tight' : '') + '">' + items.map(function (t, i) {
      return '<span class="s' + (i === active ? ' active' : '') + '" style="font-family:var(--font)">' + t + '</span>';
    }).join('') + '</div>';
  }

  function dock(o) {
    var s = '<section class="dock"' + (o.style ? ' style="' + o.style + '"' : '') + '><div class="tabs">';
    o.tabs.forEach(function (t, i) {
      s += '<div class="tab' + (i === (o.active || 0) ? ' active' : '') + '">' + (t.icon ? ic(t.icon, 'sm') : '') + t.t + (t.n != null ? ' <span class="n">' + t.n + '</span>' : '') + '</div>';
    });
    s += '<div class="tools">' + (o.tools || '');
    if (o.plainTools !== true) {
      s += '<button class="btn icon xs ghost" title="列设置">' + ic('columns', 'sm') + '</button><button class="btn icon xs ghost" title="最大化">' + ic('maximize', 'sm') + '</button>';
    }
    s += '</div></div>';
    if (o.banner) s += o.banner;
    if (o.toolbar) s += '<div class="toolbar">' + o.toolbar + '</div>';
    s += '<div class="dock-body">' + o.body + '</div>';
    if (o.foot) s += '<div class="dock-foot">' + o.foot + '</div>';
    return s + '</section>';
  }

  function sec(title, icon, rows, o) {
    o = o || {};
    var s = '<div class="sec' + (o.collapsed ? ' collapsed' : '') + '"><div class="sh">' + ic(o.collapsed ? 'chevright' : icon, 'sm') + title
      + (o.rt ? '<span class="rt">' + o.rt + '</span>' : '') + '</div>';
    if (!o.collapsed) {
      if (o.before) s += o.before;
      if (rows && rows.length) {
        // A row whose label is null is placed as it is: a validation message under a field.
        s += '<div class="form">' + rows.map(function (r) {
          return r[0] === null ? r[1] : '<label>' + r[0] + '</label>' + r[1];
        }).join('') + '</div>';
      }
      if (o.after) s += o.after;
    }
    return s + '</div>';
  }

  function side(o) {
    var s = '<aside class="side"><div class="hdr"><div class="ic">' + ic(o.icon, 'lg') + '</div><div class="grow" style="min-width:0"><div class="ttl">' + o.title + '</div><div class="sub">' + o.sub + '</div></div><button class="btn icon sm ghost" title="更多操作">' + ic('more') + '</button></div>';
    if (o.tabs) {
      s += '<div class="tabs">' + o.tabs.map(function (t, i) {
        return '<div class="tab' + (i === (o.tab || 0) ? ' active' : '') + (t === '更多' ? ' more' : '') + '">' + t + (t === '更多' ? ic('chevdown', 'sm') : '') + '</div>';
      }).join('') + '</div>';
    }
    s += '<div class="body">' + o.body + '</div>';
    if (o.foot !== false) {
      s += '<div class="foot">' + (o.foot || '<button class="btn">重置</button><button class="btn primary">应用</button>') + '</div>';
    }
    return s + '</aside>';
  }

  function inp(text, o) {
    o = o || {};
    return '<div class="inp' + (o.mono ? ' mono' : '') + (o.ro ? ' ro' : '') + '"' + (o.w ? ' style="flex:0 0 ' + o.w + 'px;width:' + o.w + 'px"' : '') + '>'
      + (o.pre ? '<span class="pre">' + o.pre + '</span>' : '') + text + (o.u ? '<span class="u">' + o.u + '</span>' : '')
      + (o.select ? ic('chevdown', 'sm caret') : '') + '</div>';
  }

  function toggle(on, text) {
    return '<div class="row"><span class="toggle' + (on ? ' on' : '') + '"></span>' + (text ? '<span class="t2">' + text + '</span>' : '') + '</div>';
  }

  function app(o) {
    var cls = 'app side-500' + (o.side ? '' : ' no-side');
    var s = '<div class="' + cls + '">' + topbar(o.top) + rail(o.page, o.badges)
      + '<main class="center' + (o.centerClass ? ' ' + o.centerClass : '') + '">' + o.center + '</main>'
      + (o.side || '') + status(o.status) + '</div>' + (o.overlay || '');
    document.body.insertAdjacentHTML('beforeend', s);
    var cam = document.getElementById('cam');
    if (cam && o.scene) cam.innerHTML = o.scene;
  }

  return {
    ic: ic, app: app, topbar: topbar, rail: rail, status: status, strip: strip, camera: camera,
    dro: dro, table: table, btn: btn, sep: sep, filter: filter, seg: seg, dock: dock, sec: sec,
    side: side, inp: inp, toggle: toggle
  };
})();
