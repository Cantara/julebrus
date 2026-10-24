package no.cantara.julebrus.landing;

/**
 * The landing page's style sheet, inlined into the page (the page's CSP allows inline styles only).
 * The Julestue palette of the app (frontend/src/style.css), no web fonts: a serif stack that every
 * platform has. All motion sits behind prefers-reduced-motion: no-preference.
 */
final class LandingCss {

    private LandingCss() {
    }

    static final String CSS = """
            :root{--bg:#f6efe3;--paper:#fffaf1;--ink:#2b1a14;--muted:#6b5246;--brand:#7a1f2b;--pine:#234b36;--brass:#8a5f1c;
            --candle:#f0b45a;--line:#e3d3bb;--bar-a:#7a1f2b;--bar-b:#234b36;--hi:#c98a2b;--focus:#7a1f2b;
            --night1:#240c11;--night2:#4e1520;--night3:#7a1f2b;--cream:#fff6e8;--cream-2:#ecdcc4;color-scheme:light dark}
            @media (prefers-color-scheme:dark){:root{--bg:#1b120e;--paper:#271b16;--ink:#f1e6d4;--muted:#b9a390;--brand:#e06a70;
            --pine:#6fb98c;--brass:#d4a04f;--line:#3d2c24;--bar-a:#d0545b;--bar-b:#5c9e76;--hi:#f0b45a;--focus:#f0b45a;
            --night1:#0f0806;--night2:#2a0d12;--night3:#4e1520}}
            *{box-sizing:border-box}
            html{-webkit-text-size-adjust:100%;scroll-behavior:smooth}
            @media (prefers-reduced-motion:reduce){html{scroll-behavior:auto}}
            body{margin:0;background:var(--bg);color:var(--ink);font:17px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;overflow-x:hidden}
            a{color:var(--brand);text-underline-offset:.18em}
            a:hover{text-decoration-thickness:2px}
            :focus-visible{outline:3px solid var(--focus);outline-offset:3px;border-radius:6px}
            h1,h2,h3,.display,.facts strong,.fbrand{font-family:"Iowan Old Style","Palatino Linotype",Palatino,"Book Antiqua",Georgia,"Times New Roman",serif}
            h2{font-size:clamp(1.9rem,4.6vw,3rem);line-height:1.06;letter-spacing:-.015em;margin:.1em 0 .6em;text-wrap:balance}
            h3{font-size:1.2rem;line-height:1.25;margin:0 0 .3em}
            code{font:.88em ui-monospace,SFMono-Regular,Menlo,monospace;background:color-mix(in srgb,var(--line) 60%,transparent);padding:.05em .35em;border-radius:5px}
            .sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}
            .skip{position:absolute;left:-9999px;top:12px;z-index:10;background:var(--paper);color:var(--ink);padding:10px 14px;border-radius:10px}
            .skip:focus{left:16px}
            .wrap{max-width:70rem;margin:0 auto;padding:0 16px}
            @media (min-width:720px){.wrap{padding:0 32px}}
            .muted{color:var(--muted)}
            .eyebrow{text-transform:uppercase;letter-spacing:.16em;font-size:.78rem;font-weight:700;color:var(--brass);margin:0}
            section{padding:64px 0}
            @media (min-width:900px){section{padding:96px 0}}
            .band{background:var(--paper);border-block:1px solid var(--line)}
            .two{display:grid;gap:40px}
            @media (min-width:960px){.two{grid-template-columns:1fr 1fr;align-items:start;gap:56px}}
            .prose p{font-size:1.08rem;max-width:36rem;margin:0 0 1em}
            .intro{font-size:1.12rem;max-width:44rem}
            .note{font-size:.84rem;color:var(--muted);margin:.8em 0 0}

            /* hero */
            .hero{position:relative;overflow:hidden;color:var(--cream);--focus:#f0b45a;
            background:radial-gradient(60rem 30rem at 78% 8%,rgba(240,180,90,.30),transparent 60%),
            radial-gradient(40rem 24rem at 0% 100%,rgba(35,75,54,.45),transparent 70%),
            linear-gradient(168deg,var(--night1) 0%,var(--night2) 55%,var(--night3) 100%)}
            .hero a{color:var(--cream)}
            .hero-in{position:relative;z-index:2;padding-top:58px;padding-bottom:40px}
            .top{display:flex;justify-content:space-between;align-items:center;font-size:.95rem}
            .brand{font-family:"Iowan Old Style","Palatino Linotype",Palatino,Georgia,serif;font-size:1.25rem;font-weight:700;letter-spacing:.02em}
            .lang{display:inline-block;padding:6px 14px;border:1px solid rgba(255,246,232,.4);border-radius:999px;text-decoration:none;font-size:.9rem}
            .lang:hover{background:rgba(255,246,232,.1)}
            .hero-grid{display:grid;gap:24px;align-items:center;padding:48px 0 36px}
            @media (min-width:860px){.hero-grid{grid-template-columns:1.5fr 1fr;padding:72px 0 56px}}
            .kicker{text-transform:uppercase;letter-spacing:.18em;font-size:.78rem;font-weight:700;color:var(--candle);margin:0 0 18px}
            h1{font-size:clamp(3rem,12vw,6.4rem);line-height:.95;letter-spacing:-.03em;margin:0 0 24px;font-weight:700;text-wrap:balance}
            h1 .yr{display:block;font-style:italic;font-weight:400;color:var(--candle)}
            .lead{font-size:clamp(1.08rem,2.4vw,1.3rem);max-width:34rem;color:var(--cream-2);margin:0 0 28px}
            .cta{display:flex;flex-wrap:wrap;gap:12px;margin:0 0 22px}
            .btn{display:inline-flex;align-items:center;gap:10px;padding:14px 22px;border-radius:999px;background:var(--candle);color:#2b1a14 !important;
            font-weight:700;text-decoration:none;box-shadow:0 12px 32px -12px rgba(240,180,90,.75);border:1.5px solid var(--candle)}
            .btn:hover{background:#f5c87a}
            .btn.ghost{background:transparent;color:var(--cream) !important;border-color:rgba(255,246,232,.5);box-shadow:none}
            .btn.ghost:hover{background:rgba(255,246,232,.1)}
            .g{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}
            .sponsor{margin:0;font-size:.92rem;color:var(--cream-2)}
            .die{width:min(40vw,250px);justify-self:center;filter:drop-shadow(0 26px 40px rgba(0,0,0,.45));transform:rotate(-9deg)}
            @media (max-width:859px){.die{display:none}}
            .facts{list-style:none;margin:0;padding:22px 0 0;border-top:1px solid rgba(255,246,232,.2);display:grid;grid-template-columns:repeat(2,1fr);gap:18px 20px}
            @media (min-width:860px){.facts{grid-template-columns:repeat(4,1fr)}}
            .facts strong{display:block;white-space:nowrap;font-size:clamp(1.7rem,4.4vw,2.5rem);line-height:1.05;color:var(--candle);font-variant-numeric:lining-nums tabular-nums}
            .facts .arrow{font-size:.7em;padding:0 .15em;opacity:.8}
            .facts li>span{display:block;margin-top:4px;font-size:.88rem;line-height:1.35;color:var(--cream-2)}
            .snow{position:absolute;inset:0;z-index:1;pointer-events:none;opacity:.85;
            background-image:radial-gradient(2.2px 2.2px at 23px 37px,#fff 60%,transparent 62%),radial-gradient(1.6px 1.6px at 141px 83px,#fff 60%,transparent 62%),
            radial-gradient(2.6px 2.6px at 287px 171px,#fff 60%,transparent 62%),radial-gradient(1.4px 1.4px at 74px 236px,#fff 60%,transparent 62%),
            radial-gradient(2px 2px at 203px 301px,#fff 60%,transparent 62%);background-size:331px 347px}
            .snow.far{opacity:.45;background-size:173px 211px;background-position:61px 29px;filter:blur(.4px)}
            .lights{position:absolute;z-index:2;top:0;left:0;right:0;height:40px;display:flex;justify-content:space-around;padding:0 2%}
            .lights::before{content:"";position:absolute;left:-2%;right:-2%;top:-26px;height:40px;border-bottom:1.5px solid rgba(255,246,232,.35);border-radius:0 0 50% 50%/0 0 100% 100%}
            .lights i{position:relative;width:9px;height:13px;margin-top:9px;border-radius:50% 50% 45% 45%/60% 60% 40% 40%;background:#f0b45a;box-shadow:0 0 12px 3px rgba(240,180,90,.55)}
            .lights i:nth-child(4n+2){background:#e5565e;box-shadow:0 0 12px 3px rgba(229,86,94,.5)}
            .lights i:nth-child(4n+3){background:#7cc596;box-shadow:0 0 12px 3px rgba(124,197,150,.45)}
            .lights i:nth-child(4n){background:#f7dc96;box-shadow:0 0 12px 3px rgba(247,220,150,.5)}
            .lights i:nth-child(n+3):nth-child(-n+16){margin-top:13px}
            .lights i:nth-child(n+6):nth-child(-n+13){margin-top:16px}
            @media (max-width:600px){.lights i:nth-child(n+12){display:none}}

            /* rules */
            .table-wrap{border:1px solid var(--line);border-radius:20px;background:var(--paper);overflow:hidden}
            .rules{width:100%;border-collapse:collapse}
            .rules th,.rules td{text-align:left;vertical-align:top;padding:16px 18px;border-bottom:1px solid var(--line)}
            .rules tr:last-child>*{border-bottom:0}
            .rules thead th{font-size:.78rem;text-transform:uppercase;letter-spacing:.12em;color:var(--muted);background:color-mix(in srgb,var(--line) 35%,transparent)}
            .rules th[scope=row]{font-family:"Iowan Old Style","Palatino Linotype",Palatino,Georgia,serif;font-size:1.15rem;white-space:nowrap;width:9rem}
            .rules td.val{font-weight:600;width:38%}
            .rules td.why{color:var(--muted)}
            .pips{display:inline-flex;gap:4px;vertical-align:-5px;margin-right:4px;color:var(--brand)}.pips .face{width:22px;height:22px;color:var(--brand)}
            .w{display:block;font-weight:500}
            .w b{display:inline-block;min-width:2.6rem;text-align:center;margin:0 6px 4px 0;padding:1px 8px;border-radius:999px;background:var(--candle);color:#2b1a14;font-variant-numeric:tabular-nums}
            @media (max-width:700px){.rules thead{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
            .rules tr{display:block;padding:16px 18px;border-bottom:1px solid var(--line)}.rules tr:last-child{border-bottom:0}
            .rules th,.rules td{display:block;padding:0;border:0;width:auto !important}
            .rules td.val{margin:4px 0}}

            /* how it works */
            .steps{list-style:none;margin:0;padding:0;display:grid;gap:16px;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr))}
            .step{background:var(--bg);border:1px solid var(--line);border-radius:20px;padding:22px}
            .step p{margin:0;color:var(--muted)}
            .ic{width:44px;height:44px;padding:10px;border-radius:14px;background:var(--brand);stroke:#fff6e8;fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round;margin-bottom:14px}
            @media (prefers-color-scheme:dark){.ic{background:#7a1f2b}}
            .badges-h{margin-top:48px}
            .badges{list-style:none;margin:12px 0 0;padding:0;display:grid;gap:10px 22px;grid-template-columns:repeat(auto-fit,minmax(min(100%,290px),1fr))}
            .badges li{display:flex;gap:12px;align-items:baseline;color:var(--muted);font-size:.95rem}
            .badges b{color:var(--ink);margin-right:4px}
            .bauble{flex:none;width:14px;height:14px;border-radius:50%;transform:translateY(2px);background:radial-gradient(circle at 35% 30%,#fff6 0 20%,transparent 22%),var(--b,#7a1f2b)}
            .badges li:nth-child(3n+2) .bauble{--b:#c98a2b}.badges li:nth-child(3n) .bauble{--b:#2f6b45}

            /* history */
            .provenance{border:1px solid var(--line);border-left:5px solid var(--brass);border-radius:16px;background:var(--paper);padding:18px 22px;margin:28px 0 32px}
            .provenance h3{font-size:1.05rem}
            .provenance ul{margin:0;padding-left:1.1em;color:var(--muted);font-size:.95rem}
            .provenance li{margin:.25em 0}
            .grid{display:grid;gap:24px}
            @media (min-width:960px){.grid{grid-template-columns:1fr 1fr;align-items:start}.chart.wide{grid-column:1/-1}}
            .chart{margin:0;background:var(--paper);border:1px solid var(--line);border-radius:22px;padding:22px 20px;min-width:0;
            box-shadow:0 1px 0 rgba(43,26,20,.04),0 18px 40px -30px rgba(43,26,20,.45)}
            .band .chart{background:var(--bg)}
            .chart figcaption{margin-bottom:18px}
            .chart figcaption p{margin:0;font-size:.92rem}
            .cols{display:flex;gap:5px;height:210px;align-items:stretch}
            .col{flex:1;min-width:0;display:grid;grid-template-rows:1.3em 1fr auto;justify-items:center}
            .col .val{font-size:.74rem;color:var(--muted);font-variant-numeric:tabular-nums;white-space:nowrap}
            .col .area{display:flex;align-items:flex-end;width:100%;height:100%;justify-content:center}
            .col .bar{display:block;width:100%;max-width:40px;height:var(--v);min-height:2px;background:var(--bar-a);border-radius:4px 4px 0 0;transform-origin:bottom}
            .col.hi .bar{background:var(--hi)}
            .col.gap .bar{height:8px;background:none;border:1.5px dashed var(--line);border-bottom:0}
            .col .x{font-size:.74rem;color:var(--muted);margin-top:6px;font-variant-numeric:tabular-nums}
            .cols::after{content:none}
            .dice-cols{height:230px}
            .face{width:26px;height:26px;color:var(--ink)}
            .face rect{fill:none;stroke:currentColor;stroke-width:1.6}.face circle{fill:currentColor}
            .multi{display:grid;gap:4px}
            .mh,.mr{display:grid;grid-template-columns:3.4rem 1fr 1fr;gap:12px;align-items:center}
            .mh span{font-size:.78rem;text-transform:uppercase;letter-spacing:.1em;color:var(--muted);font-weight:700}
            .mr .y,.tops .y,.dr .y{font-variant-numeric:tabular-nums;font-weight:600;font-size:.92rem}
            .hb{display:flex;align-items:center;gap:8px;min-width:0}
            .hb .fill{display:block;height:14px;width:calc(var(--v)*.8);min-width:2px;background:var(--bar-a);border-radius:0 4px 4px 0;transform-origin:left}
            .hb.b .fill{background:var(--bar-b)}
            .hb .num{font-size:.84rem;font-variant-numeric:tabular-nums}
            .mr.agg .fill{background:repeating-linear-gradient(135deg,var(--bar-a) 0 4px,transparent 4px 7px);outline:1px solid var(--bar-a)}
            .mr.agg .hb.b .fill{background:repeating-linear-gradient(135deg,var(--bar-b) 0 4px,transparent 4px 7px);outline-color:var(--bar-b)}
            .tops{list-style:none;margin:0;padding:0}
            .tops li{display:grid;grid-template-columns:3.4rem 1fr auto;gap:12px;align-items:baseline;padding:8px 0;border-bottom:1px dashed var(--line)}
            .tops li:last-child{border-bottom:0}
            .tops .b{font-weight:600}
            .tops .m{font-variant-numeric:tabular-nums;text-align:right;white-space:nowrap}.tops .m b{color:var(--brand)}
            .tops small{font-weight:400;color:var(--muted);font-size:.76rem;margin-right:10px;white-space:nowrap}
            .dots{display:grid;gap:2px}
            .axis,.dr{display:grid;grid-template-columns:3.4rem 1fr 3rem;gap:12px;align-items:center}
            .ticks{position:relative;height:1.3em}
            .ticks span{position:absolute;left:var(--p);transform:translateX(-50%);font-size:.72rem;color:var(--muted);font-variant-numeric:tabular-nums}
            .track{position:relative;height:22px;background:linear-gradient(var(--line),var(--line)) center/100% 1px no-repeat}
            .dot{position:absolute;left:var(--p);top:50%;width:13px;height:13px;border-radius:50%;background:var(--bar-a);transform:translate(-50%,-50%);box-shadow:0 0 0 2px var(--paper)}
            .dr.kind .dot{background:var(--hi);width:16px;height:16px}
            .dr.strict .dot{background:var(--bar-b);width:16px;height:16px}
            .dr .num{font-size:.84rem;font-variant-numeric:tabular-nums;text-align:right}
            .regs{list-style:none;margin:0;padding:0;display:grid;gap:8px}
            .regs li{display:grid;grid-template-columns:minmax(6.5rem,9rem) 1fr auto;gap:12px;align-items:center}
            .regs .b{font-weight:600;font-size:.95rem;overflow-wrap:anywhere}
            .regs .v{font-size:.8rem;color:var(--muted);font-variant-numeric:tabular-nums;white-space:nowrap}
            details{margin-top:16px;font-size:.9rem}
            summary{cursor:pointer;color:var(--brass);font-weight:600;width:max-content;max-width:100%}
            .data{width:100%;border-collapse:collapse;margin-top:10px;font-variant-numeric:tabular-nums}
            .data th,.data td{text-align:left;padding:6px 8px;border-bottom:1px solid var(--line)}
            .data th{font-size:.78rem;color:var(--muted)}

            /* privacy */
            .plain{list-style:none;margin:0;padding:0;display:grid;gap:14px}
            .plain li{position:relative;padding:16px 18px 16px 50px;background:var(--bg);border:1px solid var(--line);border-radius:16px}
            .plain li::before{content:"";position:absolute;left:18px;top:20px;width:16px;height:9px;border-left:2.5px solid var(--pine);border-bottom:2.5px solid var(--pine);transform:rotate(-45deg)}
            .more{font-weight:700}

            /* footer */
            .foot{background:linear-gradient(180deg,var(--night2),var(--night1));color:var(--cream-2);padding:56px 0 calc(48px + env(safe-area-inset-bottom));--focus:#f0b45a}
            .foot a{color:var(--cream)}
            .fbrand{font-size:1.6rem;font-weight:700;color:var(--cream);margin:0 0 18px}
            .fbrand span{font-weight:400;font-size:1rem;color:var(--cream-2)}
            .links{display:flex;flex-wrap:wrap;gap:10px 22px;align-items:center;margin:8px 0 0;font-size:.95rem}

            @media (prefers-reduced-motion:no-preference){
            .snow{animation:fall 16s linear infinite}.snow.far{animation-duration:26s}
            @keyframes fall{from{background-position:0 0,0 0,0 0,0 0,0 0}to{background-position:30px 347px,-20px 347px,10px 347px,-30px 347px,20px 347px}}
            .snow.far{animation-name:fallfar}
            @keyframes fallfar{from{background-position:0 0,0 0,0 0,0 0,0 0}to{background-position:-15px 211px,10px 211px,-5px 211px,15px 211px,-10px 211px}}
            .lights i{animation:twinkle 2.8s ease-in-out infinite alternate}
            .lights i:nth-child(3n){animation-delay:-.9s}.lights i:nth-child(3n+1){animation-delay:-1.7s}.lights i:nth-child(5n){animation-duration:3.6s}
            @keyframes twinkle{0%,60%{opacity:1}100%{opacity:.45;box-shadow:none}}
            .die{animation:bob 7s ease-in-out infinite}
            @keyframes bob{0%,100%{transform:rotate(-9deg) translateY(0)}50%{transform:rotate(-4deg) translateY(-10px)}}
            .hero-grid>div>*{animation:up .8s cubic-bezier(.2,.8,.2,1) both}
            .hero-grid>div>:nth-child(2){animation-delay:.08s}.hero-grid>div>:nth-child(3){animation-delay:.16s}.hero-grid>div>:nth-child(4){animation-delay:.24s}
            @keyframes up{from{opacity:0;transform:translateY(14px)}}
            .col .bar{animation:rise 1s cubic-bezier(.2,.8,.2,1) both}
            .hb .fill{animation:grow 1s cubic-bezier(.2,.8,.2,1) both}
            @keyframes rise{from{transform:scaleY(0)}}
            @keyframes grow{from{transform:scaleX(0)}}
            .btn{transition:background .2s,transform .2s}.btn:hover{transform:translateY(-1px)}
            }
            @media (forced-colors:active){.col .bar,.hb .fill,.dot{background:CanvasText}.snow,.lights{display:none}}
            """;
}
