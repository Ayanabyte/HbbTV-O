(function(){
 'use strict';
 if(location.origin!=='https://hbbtv.lovestv.es'||location.pathname.indexOf('/lovestv3-4.0.0/')!==0)return;
 if(window.__hbbDesign41){window.__hbbDesign41.tick();return;}
 var state={active:false,topReady:false,request:0,selectedAt:0,requestPending:false,focusZone:'none'},lastEPG=0,bgUrl='';
 var css="/* Applied only to the verified LOVEStv home view; player/dialog routes retain original geometry. */\nhtml.hbb-home41 body { background:#101114; }\n.hbb-home41 ._sideNavWrapper_1i0iu_26 { top:140px;height:568px; }\n.hbb-home41 ._sideNav_1i0iu_26 { height:568px; }\n.hbb-home41 ._sideNav_1i0iu_26 ._sideNavMenu_1i0iu_58 { padding-top:6px; }\n.hbb-home41 ._sideNav_1i0iu_26 ._sideNavMenu_1i0iu_58 ._logoWrapper_1i0iu_62 { display:none; }\n.hbb-home41 ._sideNav_1i0iu_26 ._sideNavMenu_1i0iu_58 ._sideNavItem_1i0iu_79 { margin-top:12px;margin-bottom:12px; }\n.hbb-home41 ._homeViewContainer_1qwjv_26 { background:#101114; }\n.hbb-home41 ._container_ksdnj_1 { top:140px;left:0;right:auto;width:970px;height:560px;overflow:visible; }\n.hbb-home41 ._container_ksdnj_1 ._fadeTransition_f97ma_26 { width:970px;height:545px;object-fit:cover; }\n.hbb-home41 ._container_ksdnj_1 ._programInfo_ksdnj_1 { top:18px;left:92px;padding-left:0;width:840px;max-height:178px;font-size:17px; }\n.hbb-home41 ._programInfo_ksdnj_1 h1 { font-size:32px;line-height:38px;margin:8px 0;max-height:76px;overflow:hidden; }\n.hbb-home41 ._programInfo_ksdnj_1 ._channelLogo_a9chu_26 { max-height:30px; }\n.hbb-home41 ._programInfo_ksdnj_1 ._description_a9chu_71 { width:790px;font-size:15px;line-height:19px;height:57px;margin-top:10px; }\n.hbb-home41 ._homeViewContainer_1qwjv_26 ._carouselWrapper_1qwjv_31 { top:336px;width:958px;max-height:none;height:362px;padding-top:7px; }\n.hbb-home41 ._container_1n3nb_1 { margin-left:92px;height:170px;margin-bottom:30px; }\n.hbb-home41 ._container_1n3nb_1 ._title_1n3nb_1 { font-size:20px;line-height:25px;margin-bottom:8px; }\n.hbb-home41 ._container_1whzl_26._focused_1whzl_37 { transform:scale(1.035); }\n.hbb-home41 #broadcast { left:990px!important;top:154px!important;width:270px!important;height:152px!important; }\n.hbb-home41 ._broadcastPlaceholder_1r8j8_26 { position:fixed!important;left:990px!important;top:154px!important;width:270px!important;height:152px!important; }\n.hbb-home41 ._broadcastPlaceholder_1r8j8_26:focus:after { left:-3px;top:-3px;width:270px;height:152px;box-sizing:content-box;border:3px solid white; }\n.hbb-home41 ._yellowButtonContainer_nxeu9_26 { position:fixed!important;left:990px!important;right:auto!important;top:420px!important;width:258px;font-size:15px;padding:8px 4px;background:#16181c; }\n.hbb-home41 #hbb-sidebar41 { position:fixed;left:976px;top:320px;width:284px;height:369px;border-left:1px solid #34363b;padding-left:14px;color:#f7f7f8;z-index:16;pointer-events:none;box-sizing:border-box; }\n#hbb-sidebar41 { display:none; }\n.hbb-home41 #hbb-sidebar41 { display:block; }\n#hbb-sidebar41 .hbb-channel41 { font-size:19px;font-weight:bold; }\n#hbb-sidebar41 .hbb-live41 { color:#ff4049;font-size:12px;margin-left:9px; }\n#hbb-sidebar41 .hbb-nowtitle41 { font-size:22px;line-height:27px;max-height:54px;overflow:hidden;margin-top:12px; }\n#hbb-sidebar41 .hbb-time41 { font-size:14px;color:#b8bbc3;margin-top:6px; }\n#hbb-sidebar41 .hbb-next41 { margin-top:97px;border-top:1px solid #34363b;padding-top:18px; }\n#hbb-sidebar41 .hbb-nextlabel41 { color:#b8bbc3;font-size:16px;margin-bottom:10px; }\n#hbb-sidebar41 .hbb-nexttitle41 { font-size:18px;line-height:23px;max-height:69px;overflow:hidden; }\n#hbb-sidebar41 .hbb-note41 { color:#b8bbc3;font-size:13px;margin-top:18px; }\n.hbb-home41 [tabindex]:focus { outline-color:#fff; }\n\n.hbb-home41.hbb-header41 ._container_1whzl_26._focused_1whzl_37 ._background_1whzl_42:first-of-type:after { display:none; }\n\n/* Replace only the home backdrop: original canvases contain a transparent hole\n   at the obsolete TV-preview coordinates. The actual broadcast is untouched. */\n#hbb-hero42 { display:none; }\n.hbb-home41 #hbb-hero42 { display:block;position:absolute;left:0;top:0;width:970px;height:560px;background-color:#101114;background-position:center top;background-size:cover;pointer-events:none;z-index:0; }\n.hbb-home41 ._container_ksdnj_1 canvas._fadeTransition_f97ma_26 { visibility:hidden!important; }\n.hbb-home41 #hbb-focus22 { display:none!important; }\n.hbb-home41 ._container_1whzl_26._focused_1whzl_37 ._background_1whzl_42:first-of-type:after { display:none; }\n#hbb-focus42 { display:none;position:fixed;z-index:2147483646;border:3px solid #fff;border-radius:7px;box-sizing:border-box;background:transparent;box-shadow:0 0 0 1px #111,0 0 8px rgba(255,255,255,.3);pointer-events:none; }\n.hbb-selector43 #hbb-focus22 { display:none!important; }\n.hbb-selector43 ._container_1whzl_26._focused_1whzl_37 ._background_1whzl_42:first-of-type:after { display:none; }\n\n/* Keep only the inner sliding strip wide. SpatialNavigation measures the outer\n   focusable element, which must fit the visible catalogue column. */\n.hbb-home41 ._container_1n3nb_1 { width:866px; }\n.hbb-home41 ._wrapper_1n3nb_47 { width:866px;display:block; }\n.hbb-home41 ._container_1mfsn_26 { width:866px!important;height:138px; }\n\n/* Pure black surfaces; artwork, thumbnails and the live broadcast stay visible. */\n.hbb-black44 body,.hbb-black44 ._homeViewContainer_1qwjv_26 { background-color:#000!important; }\n.hbb-black44 #hbb-hero42 { background-color:#000!important; }\n.hbb-black44 ._sideNav_1i0iu_26 { background-color:#000!important; }\n.hbb-black44 ._sideNavBackground_1i0iu_41 { opacity:0!important; }\n.hbb-black44 ._sideNavWrapper_1i0iu_26._overlay_1i0iu_36 { background-color:transparent!important; }\n.hbb-black44 ._sideNavItemContent_12o78_32,.hbb-black44 ._sideNavItemWrapper_12o78_26:focus { background:#000!important;color:#fff!important; }\n.hbb-black44 ._sideNavIconWrapper_12o78_52 img._baseColor_12o78_64 { display:inline-block!important; }\n.hbb-black44 ._sideNavIconWrapper_12o78_52 img._invertedColor_12o78_67 { display:none!important; }\n.hbb-black44 ._yellowButtonContainer_nxeu9_26,.hbb-black44 ._background_1whzl_42 { background-color:#000!important; }\n.hbb-black44 .settingsFocusable,.hbb-black44 .dialogFocusable,.hbb-black44 .programInfoFocusable { background-color:#000!important;color:#fff!important; }\n.hbb-black44 [class*=\"_genre_\"],.hbb-black44 [class*=\"_rating_\"],.hbb-black44 [class*=\"_settingsModal_\"],.hbb-black44 [class*=\"_modalBackground_\"] { background-color:#000!important; }\n.hbb-black44 #epgGrid,.hbb-black44 .epgGrid,.hbb-black44 .epgChannel,.hbb-black44 .epgGridProgram,.hbb-black44 .epgGridPastProgram,.hbb-black44 .epgGridFocusProgram { background:#000!important; }\n.hbb-black44 ._broadcastPlaceholder_1r8j8_26 { background:transparent!important; }\n";
 function visible(el){if(!el)return false;var r=el.getBoundingClientRect(),s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';}
 function playing(){var p=window.player,root=document.getElementById('videoPlayer');return !!(p&&p.visible)||!!(root&&/(^|\s)visible(\s|$)/.test(root.className))||visible(document.getElementById('_vue-hbbtv-player-videoPlayer'));}
 function blocked(){return visible(document.querySelector('.dialog'))||visible(document.querySelector('[role="dialog"]'));}
 function el(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
 function sidebar(){
  var box=document.getElementById('hbb-sidebar41');if(box)return box;
  box=el('aside');box.id='hbb-sidebar41';box.setAttribute('aria-hidden','true');
  var ch=el('div','hbb-channel41');ch.appendChild(el('span','hbb-channelname41'));ch.appendChild(el('span','hbb-live41','EN DIRECTO'));box.appendChild(ch);
  box.appendChild(el('div','hbb-nowtitle41'));box.appendChild(el('div','hbb-time41'));
  var next=el('div','hbb-next41');next.appendChild(el('div','hbb-nextlabel41','Después'));next.appendChild(el('div','hbb-nexttitle41'));next.appendChild(el('div','hbb-time41 hbb-nexttime41'));box.appendChild(next);
  document.body.appendChild(box);return box;
 }
 function time(v){if(!v||!isFinite(v))return '';var d=new Date(v*1000);return ('0'+d.getHours()).slice(-2)+':'+('0'+d.getMinutes()).slice(-2);}
 function programme(list,i){try{return list.item?list.item(i):list[i];}catch(ignore){return null;}}
 function epg(){
  var box=sidebar(),cfg=window.__hbbHeader41||{},b=document.getElementById('broadcast');
  box.querySelector('.hbb-channelname41').textContent=cfg.channel||'TV';
  if(Date.now()-lastEPG<2000)return;lastEPG=Date.now();
  var current=null,next=null;try{if(b&&b.programmes){current=programme(b.programmes,0);next=programme(b.programmes,1);}}catch(ignore){}
  box.querySelector('.hbb-nowtitle41').textContent=current&&current.name?String(current.name):'Directo del canal';
  box.querySelector('.hbb-time41').textContent=current?time(current.startTime)+(current.duration?' – '+time(current.startTime+current.duration):''):'';
  box.querySelector('.hbb-nexttitle41').textContent=next&&next.name?String(next.name):'Programación no disponible';
  box.querySelector('.hbb-nexttime41').textContent=next?time(next.startTime):'';
 }
 function focused(){var a=document.activeElement;if(a&&a!==document.body&&visible(a))return a;return document.querySelector('._container_1whzl_26._focused_1whzl_37');}
 function zone(a){
  if(!a||a===document.body)return 'none';
  if(a.closest('._sideNavWrapper_1i0iu_26'))return 'nav';
  if(a.closest('[role="dialog"],.dialog')||/(^|\s)dialogFocusable(\s|$)/.test(a.className||''))return 'dialog';
  if(a.closest('._broadcastPlaceholder_1r8j8_26'))return 'preview';
  if(a.closest('._container_1mfsn_26')||a.querySelector('._container_1whzl_26'))return 'rail';
  return 'control';
 }
 function rows(){return Array.prototype.filter.call(document.querySelectorAll('._homeViewContainer_1qwjv_26 ._container_1mfsn_26.focusable'),visible);}
 function moveInRow(a,key){
  var row=a.closest('._container_1mfsn_26');if(!row)return false;
  try{
   var vm=row.__vue__,proxies=vm?[vm._setupState,vm._setupProxy,vm._self&&vm._self._setupProxy]:[];
   for(var j=0;j<proxies.length;j++){
    var p=proxies[j];if(!p||typeof p.move!=='function')continue;
    var props=p.props;if(props&&props.value)props=props.value;
    var position=p.position;if(position&&typeof position==='object')position=position.value;
    if(!props||!Array.isArray(props.slides)||!Number.isInteger(position)||position<0||position>=props.slides.length)continue;
    var next=position+(key===39?1:-1);
    if(next>=0&&next<props.slides.length)p.move(next);
    else{
     var edge=key===37?p.onLeft:p.onRight;
     if(typeof edge==='function')edge();
    }
    state.navigation={from:'rail',key:key,before:position,target:Math.max(0,Math.min(next,props.slides.length-1)),via:'CarouselContainer.move',accepted:true};
    // move() is the original component method: it scrolls, emits activated and
    // updates FeedComponent's real position used by OK and programme metadata.
    return true;
   }
  }catch(error){state.navigation={from:'rail',key:key,accepted:false,error:String(error).substring(0,160)};}
  return false;
 }
 function navigateContent(e){
  if(!state.active||blocked()||playing())return false;
  var a=document.activeElement,from=zone(a),list=rows(),target=null;
  if(from==='rail'&&(e.keyCode===37||e.keyCode===39)&&moveInRow(a,e.keyCode)){
   e.preventDefault();e.stopImmediatePropagation();window.setTimeout(tick,0);return true;
  }
  if(from==='preview'&&(e.keyCode===40||e.keyCode===37))target=list[0];
  else if(from==='rail'&&(e.keyCode===38||e.keyCode===40)){
   var row=a.closest('._container_1mfsn_26'),i=list.indexOf(row);
   if(i>=0){if(e.keyCode===38)target=i>0?list[i-1]:document.querySelector('._broadcastPlaceholder_1r8j8_26');else target=list[i+1];}
  }
  if(!target||!window.SpatialNavigation||typeof window.SpatialNavigation.focus!=='function')return false;
  try{
   // Use the provider's API so FeedComponent receives its real focus event,
   // activates its current card, scrolls the rail and retains normal OK/actions.
   var ok=window.SpatialNavigation.focus(target);
   state.navigation={from:from,key:e.keyCode,to:target.id||target.className,accepted:ok===true};
   if(ok===true){e.preventDefault();e.stopImmediatePropagation();return true;}
  }catch(ignore){}
  return false;
 }
 function background(){
  var hero=document.querySelector('._homeViewContainer_1qwjv_26 ._container_ksdnj_1');if(!hero)return;
  var host=document.getElementById('hbb-hero42');
  if(!host){host=el('div');host.id='hbb-hero42';host.setAttribute('aria-hidden','true');hero.insertBefore(host,hero.firstChild);}
  // The provider canvas deliberately clears the old preview rectangle. Use the
  // original component's current image URL, without changing any canvas API.
  var canvas=hero.querySelector('canvas._fadeTransition_f97ma_26'),url='';
  try{
   for(var node=canvas&&canvas.parentElement,depth=0;node&&depth<4;node=node.parentElement,depth++){
    var vm=node.__vue__;if(!vm)continue;
    var proxies=[vm._setupState,vm._setupProxy,vm._self&&vm._self._setupProxy,vm];
    for(var j=0;j<proxies.length;j++){
     var value=proxies[j]&&proxies[j].proxySrc;
     if(value&&typeof value==='object'&&'value' in value)value=value.value;
     if(typeof value==='string'&&value){url=value;break;}
    }
    if(url)break;
   }
  }catch(ignore){}
  if(!url||url===bgUrl)return;
  var resolved;try{resolved=new URL(url,location.href);if(!/^https?:$/.test(resolved.protocol)&&resolved.protocol!=='data:')return;}catch(ignore){return;}
  bgUrl=url;var image=new Image();
  image.onload=function(){if(bgUrl===url)host.style.backgroundImage='linear-gradient(90deg,rgba(16,17,20,.92),rgba(16,17,20,.18)),linear-gradient(0deg,#101114 0%,rgba(16,17,20,0) 85%),url('+JSON.stringify(resolved.href)+')';};
  image.src=resolved.href;
 }
 function selection(){
  var frame=document.getElementById('hbb-focus42'),cfg=window.__hbbHeader41||{};
  function hide(){if(frame)frame.style.display='none';state.selection=null;}
  var a=document.activeElement,focusZone=zone(a),inApp=location.origin==='https://hbbtv.lovestv.es'&&location.pathname.indexOf('/lovestv3-4.0.0/')===0;
  var scope=inApp&&!cfg.controls&&!(state.active&&cfg.choosing)&&(!playing()||focusZone==='dialog');
  document.documentElement.classList.toggle('hbb-selector43',scope);
  if(!scope){hide();return;}
  if(!a||a===document.body){
   // The provider's EPG widget uses a real selected-program element while
   // deliberately keeping document.activeElement on BODY (observed in the log).
   var epg=document.querySelector('#epgGrid .epgGridFocusProgram');
   if(epg&&visible(epg))a=epg;else{hide();return;}
  }
  var target=a,card=a.closest&&a.closest('._container_1whzl_26');
  if(card)target=card;
  else{
   var cards=a.querySelectorAll('._container_1whzl_26._focused_1whzl_37');
   if(cards.length===1)target=cards[0];
   else if(cards.length>1){hide();return;}
   else if(a.matches('._container_1mfsn_26')){
    // During Vue's focus update, obtain the real carousel position if exposed;
    // never outline the whole wide rail or assume its first card is selected.
    var vm=a.__vue__,proxy=vm&&(vm._setupState||vm._setupProxy),position=proxy&&proxy.position;
    if(position&&typeof position==='object')position=position.value;
    var slides=a.querySelectorAll('._slide_1mfsn_38');
    if(Number.isInteger(position)&&position>=0&&position<slides.length)target=slides[position].querySelector('._container_1whzl_26');
    else{hide();return;}
    if(!target){hide();return;}
   }
  }
  var box=target.getBoundingClientRect();
  var epgTarget=target.classList.contains('epgGridFocusProgram');
  if(!visible(target)||(!epgTarget&&box.width>innerWidth*.85)||box.height>innerHeight*.5||box.width<8||box.height<8){hide();return;}
  // Clip to real carousel viewports, not to an arbitrary wide focus container.
  var left=Math.max(2,box.left),top=Math.max(state.active?140:2,box.top),right=Math.min(innerWidth-2,box.right),bottom=Math.min(innerHeight-3,box.bottom);
  for(var parent=target.parentElement;parent&&parent!==document.body;parent=parent.parentElement){
   var style=getComputedStyle(parent),r=parent.getBoundingClientRect();
   if(/hidden|auto|scroll/.test(style.overflowX)){left=Math.max(left,r.left);right=Math.min(right,r.right);}
   if(/hidden|auto|scroll/.test(style.overflowY)){top=Math.max(top,r.top);bottom=Math.min(bottom,r.bottom);}
  }
  if(right-left<8||bottom-top<8){hide();return;}
  if(!frame){frame=el('div');frame.id='hbb-focus42';frame.setAttribute('aria-hidden','true');document.body.appendChild(frame);}
  frame.style.left=(left-2)+'px';frame.style.top=(top-2)+'px';frame.style.width=(right-left+4)+'px';frame.style.height=(bottom-top+4)+'px';frame.style.display='block';
  state.selection={tag:target.tagName,id:target.id||'',cls:String(target.className||'').substring(0,120),zone:focusZone,x:left,y:top,w:right-left,h:bottom-top};
 }
 function tick(){
  document.documentElement.classList.add('hbb-black44');
  var home=document.querySelector('._homeViewContainer_1qwjv_26'),nav=document.getElementById('nav-0');
  state.active=visible(home)&&!!nav&&!playing()&&!blocked();
  state.focusZone=zone(document.activeElement);
  if(window.__hbbHeader41&&window.__hbbHeader41.choosing)state.requestPending=false;
  document.documentElement.classList.toggle('hbb-home41',state.active);
  document.documentElement.classList.toggle('hbb-header41',!!(window.__hbbHeader41&&window.__hbbHeader41.choosing));
  if(!state.active){state.topReady=false;selection();return;}
  state.topReady=state.focusZone==='preview';
  background();epg();selection();
 }
 var style=el('style');style.id='hbb-style41';style.textContent=css;document.head.appendChild(style);
 document.addEventListener('keydown',function(e){
  if(!e.isTrusted||!state.active||e.repeat||blocked()||playing())return;
  if(navigateContent(e))return;
  if(e.keyCode!==38)return;
  // Never intercept Up in the provider's side menu or a catalogue rail.
  // A bounded latch prevents TCL repeated keydowns queuing multiple entries.
  if(zone(document.activeElement)!=='preview')return;
  var now=Date.now();e.preventDefault();e.stopImmediatePropagation();
  if(state.requestPending&&now-state.selectedAt<2500)return;
  state.requestPending=true;state.request++;state.selectedAt=now;
 },true);
 window.__hbbDesign41={tick:tick,state:state};
 window.setInterval(tick,250);tick();
})();
