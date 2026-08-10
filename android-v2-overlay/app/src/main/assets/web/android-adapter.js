/* HABS Android desktop-parity adapter. Native Android owns device-audio capture and radio sockets. */
(() => {
  'use strict';
  document.body.classList.add('android-app');
  const A=window.HABSAndroid||null;
  const isAndroid=!!A;
  window.HABS_PLATFORM='android';
  if(isAndroid){
    window.open=function(url){
      if(url)A.openExternal(String(url));
      let current=String(url||'');
      return {closed:false,close(){this.closed=true},focus(){},get location(){return {href:current}},set location(v){current=String(v||'');if(current)A.openExternal(current)}};
    };
  }

  function txt(sel,value){const e=document.querySelector(sel);if(e)e.textContent=value}
  function setHtmlCopy(){
    document.querySelectorAll('.side-footer span').forEach(x=>x.textContent='Android Studio Edition');
    txt('.logo-block span','BROADCASTER PRO • ANDROID');
    txt('#footerVersion','v1.1.0 Android');
    const page=document.querySelector('[data-page="settings"] .card-title em');if(page&&page.textContent==='WINDOWS')page.textContent='ANDROID';
    document.querySelectorAll('.dj-deck-drop-overlay span').forEach(x=>x.textContent='From Android storage or the HABS DJ Library');
    const drop=document.querySelector('#dropZone span');if(drop)drop.textContent='Imported copies stay inside the HABS Android library.';
    const ddrop=document.querySelector('#djLibraryDrop small');if(ddrop)ddrop.textContent='Tap to import MP3, WAV, FLAC, M4A, AAC, OGG, OPUS, AIFF or WebM from Android storage';
    document.querySelectorAll('.security-note span').forEach(x=>{x.textContent=x.textContent.replace(/Windows\/window audio capture with audio sharing enabled\.?/i,'Android device-audio capture when Android grants playback-capture permission.')});
    const sys=document.querySelector('#systemFormat');if(sys)sys.textContent='Android native playback capture starts with the broadcast session.';
    txt('#systemDeviceStatus','Android device audio • permission requested when broadcasting');
    ['captureSystem','captureSystem2'].forEach(id=>{const b=document.getElementById(id);if(b)b.textContent='ARM DEVICE AUDIO'});
    ['stopSystemCapture','stopSystemCapture2'].forEach(id=>{const b=document.getElementById(id);if(b)b.textContent='DEVICE AUDIO INFO'});
    const badge=document.querySelector('.top-title');if(badge&&!badge.querySelector('.native-android-badge')){const n=document.createElement('span');n.className='native-android-badge';n.textContent='ANDROID NATIVE';badge.appendChild(n)}
  }
  setHtmlCopy();

  function openLibraryPicker(){const input=document.getElementById('fileInput');if(input){input.value='';input.click()}}
  document.getElementById('dropZone')?.addEventListener('click',openLibraryPicker);
  document.getElementById('djLibraryDrop')?.addEventListener('click',openLibraryPicker);

  window.captureSystemAudio=async function(options={}){
    setSystemLight?.(true);
    txt('#systemDeviceStatus','ARMED • Android will capture compatible playback when broadcast starts');
    txt('#systemFormat','Android AudioPlaybackCapture • MediaProjection permission required • 48/44.1 kHz stereo');
    if(!options.quiet)toast?.('Android device audio is armed. Start Broadcast and approve the Android capture prompt.');
    return true;
  };
  window.stopSystemCapture=function(showToast=true){
    txt('#systemDeviceStatus','Device audio capture is controlled by the live broadcast session');
    txt('#systemRawDb','—');
    txt('#systemFormat','Stop the broadcast to release Android device-audio capture.');
    if(showToast)toast?.('Android device audio follows the broadcast session.');
  };
  window.testSystemAudio=async function(){
    const s=await api('/api/streams/status');
    if(!s?.capture_active)throw new Error('Start Broadcast first and approve Android device-audio capture.');
    toast?.(`Android playback capture is active${s.program_db?` • ${s.program_db}`:''}.`);
  };
  window.systemCaptureActive=()=>!!state?.lastStatus?.capture_active;

  window.detectEncoder=async function(){
    const badge=document.getElementById('engineBadge');
    if(badge){badge.textContent='Android native MP3 engine ready';badge.className='engine-badge good'}
    txt('#encoderStatus','ANDROID MP3 READY');
    return 'mp3';
  };
  window.chooseEncoderForProfile=async function(p){return {mode:'mp3',impl:'android-native',channels:isZeno?.(p?.protocol)?2:(p?.channels===1?1:2),sampleRate:+document.getElementById('sampleRate')?.value||44100,bitrateKbps:+p?.bitrate||128}};

  function currentConfigs(){
    if(typeof saveEditorToMemory==='function'&&state.selectedStreamId)saveEditorToMemory(false);
    if(typeof rememberCurrentPassword==='function')rememberCurrentPassword();
    const enabled=(state.streamProfiles||[]).filter(p=>p.enabled!==false);
    if(!enabled.length)throw new Error('Enable at least one output stream.');
    return enabled.map(p=>{
      const pass=state.streamPasswords?.[p.id]||'';
      if(!p.host?.trim())throw new Error(`${p.name}: enter the streaming server.`);
      if(!pass)throw new Error(`${p.name}: enter its source/DJ password for this session.`);
      const z=typeof isZeno==='function'&&isZeno(p.protocol);
      return {...p,password:pass,format:'mp3',codec:'mp3',channels:z?2:(p.channels===1?1:2),username:z?(p.username||'source'):p.username,mount:(p.mount&&p.mount!=='/'&&!p.mount.startsWith('/'))?'/'+p.mount:p.mount,sample_rate:+document.getElementById('sampleRate')?.value||44100};
    });
  }
  function micGain(){
    const vol=+document.getElementById('micVolume')?.value||0;
    const db=+document.getElementById('micGainDb')?.value||0;
    const muted=!!document.getElementById('micMute')?.checked;
    return muted?0:Math.max(0,Math.min(4,vol*Math.pow(10,db/20)));
  }

  window.startBroadcast=async function(isReconnect=false){
    if(state.starting||state.stopping)return;
    if(!isAndroid)throw new Error('Android native bridge is unavailable.');
    const streams=currentConfigs();
    await saveSettings(false).catch(()=>{});
    state.starting=true;state.manualStop=false;state.broadcasting=true;state.startedAt=Date.now();updateBroadcastButton({on_air:true,connected:false});
    try{
      const payload={streams,mic_enabled:micGain()>0.0001,mic_gain:micGain(),system_audio:true};
      const r=JSON.parse(A.startBroadcast(JSON.stringify(payload))||'{}');
      if(r.ok===false)throw new Error(r.error||'Android could not start the broadcast request.');
      const badge=document.getElementById('engineBadge');if(badge){badge.textContent='ANDROID • WAITING FOR DEVICE AUDIO PERMISSION';badge.className='engine-badge warn'}
      toast?.('Approve the Android device-audio capture prompt to go on air.');
      await new Promise(r=>setTimeout(r,250));await pollStatus();
    }catch(e){state.broadcasting=false;throw e}finally{state.starting=false;updateBroadcastButton()}
  };
  window.stopBroadcast=async function(showToast=true){
    if(state.stopping)return;state.stopping=true;state.manualStop=true;updateBroadcastButton();
    try{if(isAndroid)A.stopBroadcast();state.broadcasting=false;state.reconnecting=false;state.multiStatus={streams:[],connected_count:0,desired_count:0,on_air:false,connected:false,bytes_sent:0};await new Promise(r=>setTimeout(r,200));await pollStatus();if(showToast)toast?.('Android broadcast stopped and device-audio capture released.')}finally{state.stopping=false;updateBroadcastButton()}
  };
  window.pollStatus=async function(){
    try{
      const s=await api('/api/streams/status');state.lastStatus=s;state.multiStatus=s;updateStatusVisual(s);
      const desired=+s.desired_count||0,connected=+s.connected_count||0;
      if(desired===0&&!s.on_air&&!s.connecting){state.broadcasting=false;state.reconnecting=false}
      else if(desired>0){state.broadcasting=true;state.reconnecting=connected===0}
      const badge=document.getElementById('engineBadge');if(badge){if(connected>0){badge.textContent=`ANDROID LIVE • ${connected}/${Math.max(desired,connected)} OUTPUT${Math.max(desired,connected)===1?'':'S'}`;badge.className='engine-badge good'}else if(desired>0){badge.textContent='ANDROID • CONNECTING OUTPUTS';badge.className='engine-badge warn'}else{badge.textContent='Android native MP3 engine ready';badge.className='engine-badge good'}}
      updateBroadcastButton();renderStreamProfiles?.();
      return s;
    }catch(e){return null}
  };
  window.reconnectNow=async function(){const p=saveEditorToMemory(false)||selectedProfile();if(!p)throw new Error('Select an output stream first.');await api('/api/streams/reconnect?id='+encodeURIComponent(p.id),{method:'POST'});toast?.(`${p.name}: native Android reconnect started.`);await pollStatus()};

  window.HABSAndroidCallbacks={
    projectionDenied(){state.broadcasting=false;state.starting=false;state.reconnecting=false;updateBroadcastButton();toast?.('Android device-audio capture permission was not granted.',true);pollStatus().catch(()=>{})},
    imported(n){loadLibrary().then(()=>toast?.(`${n} music file${n===1?'':'s'} imported.`)).catch(e=>toast?.(e.message,true))},
    status(){pollStatus().catch(()=>{})}
  };

  setTimeout(()=>{
    setHtmlCopy();detectEncoder().catch(()=>{});
    const h=document.getElementById('djAutoHint');if(h)h.textContent='Automatically plays the local Android Music Library and preloads the next deck.';
    const note=[...document.querySelectorAll('.hint')].find(x=>x.textContent.includes('Windows'));if(note)note.textContent=note.textContent.replace(/Windows/g,'Android');
  },700);
})();
