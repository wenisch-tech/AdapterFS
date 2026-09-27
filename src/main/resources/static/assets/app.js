(() => {
const state={export:null,path:'',entries:[],sort:'name',direction:1};
const $=s=>document.querySelector(s), list=$('#file-list'), csrf=$('meta[name=csrf-token]').content, csrfHeader=$('meta[name=csrf-header]').content;
const exports=[...document.querySelectorAll('.export-link')];
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const join=(a,b)=>[a,b].filter(Boolean).join('/');
function toast(message){const el=$('#toast');el.textContent=message;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2600)}
async function api(url,options={}){options.headers={...options.headers,[csrfHeader]:csrf};const response=await fetch(url,options);if(!response.ok){let body;try{body=await response.json()}catch{body={message:response.statusText}}throw Error(body.message||'Request failed')}const text=await response.text();return text?JSON.parse(text):null}
async function load(){
  const data=await api(`/api/v1/exports/${encodeURIComponent(state.export)}/entries?path=${encodeURIComponent(state.path)}&size=500`);state.entries=data.entries;render();
}
function render(){
  const filter=$('#filter').value.toLowerCase();const entries=state.entries.filter(e=>e.name.toLowerCase().includes(filter)).sort((a,b)=>{if(a.directory!==b.directory)return a.directory?-1:1;let x=a[state.sort],y=b[state.sort];return (typeof x==='string'?x.localeCompare(y):x-y)*state.direction});
  list.innerHTML=entries.map(e=>`<tr><td><div class="file-name" data-path="${escape(e.path)}" data-directory="${e.directory}"><span class="file-kind">${e.directory?'▰':'▤'}</span><span>${escape(e.name)}</span></div></td><td class="file-meta">${e.directory?'—':formatSize(e.size)}</td><td class="file-meta">${new Date(e.modified).toLocaleString()}</td><td><button class="row-menu" data-menu="${escape(e.path)}" data-name="${escape(e.name)}">•••</button></td></tr>`).join('');
  $('#empty').classList.toggle('hidden',entries.length>0);$('#summary').textContent=`${entries.length} item${entries.length===1?'':'s'} · ${state.entries.filter(e=>!e.directory).reduce((n,e)=>n+e.size,0).toLocaleString()} bytes`;
  renderCrumbs();
}
function renderCrumbs(){let cumulative='';const parts=state.path?state.path.split('/'):[];$('#breadcrumbs').innerHTML=[`<button data-crumb="">${escape(state.export)}</button>`,...parts.map(p=>{cumulative=join(cumulative,p);return `<span>/</span><button data-crumb="${escape(cumulative)}">${escape(p)}</button>`})].join('')}
function selectExport(name){state.export=name;state.path='';exports.forEach(e=>e.classList.toggle('active',e.dataset.export===name));$('#export-title').textContent=name;load().catch(e=>toast(e.message))}
function formatSize(n){if(n<1024)return n+' B';const units=['KB','MB','GB','TB'];let i=-1;do{n/=1024;i++}while(n>=1024&&i<3);return n.toFixed(n<10?1:0)+' '+units[i]}
function modal(html){$('#modal-content').innerHTML=html;$('#modal').showModal()}
function closeModal(){$('#modal').close();$('#modal-content').innerHTML=''}
async function upload(files){for(const file of files){const form=new FormData();form.append('file',file);const box=$('#upload-progress');box.classList.remove('hidden');$('#upload-status').textContent=file.name;try{await api(`/api/v1/exports/${state.export}/upload?path=${encodeURIComponent(state.path)}`,{method:'POST',body:form});}catch(e){toast(e.message)}}$('#upload-progress').classList.add('hidden');await load()}
exports.forEach(e=>e.onclick=()=>selectExport(e.dataset.export));
$('#file-list').onclick=e=>{const row=e.target.closest('.file-name');if(row){if(row.dataset.directory==='true'){state.path=row.dataset.path;load()}else location.href=`/api/v1/exports/${state.export}/download?path=${encodeURIComponent(row.dataset.path)}`;return}const menu=e.target.closest('[data-menu]');if(menu) modal(`<h2>${escape(menu.dataset.name)}</h2><div class="dialog-actions"><button class="button secondary" id="rename">Rename</button><button class="button primary" id="delete">Delete</button></div>`),setTimeout(()=>{$('#delete').onclick=async()=>{if(confirm(`Delete ${menu.dataset.name}?`)){await api(`/api/v1/exports/${state.export}/entries?path=${encodeURIComponent(menu.dataset.menu)}`,{method:'DELETE'});closeModal();load()}};$('#rename').onclick=()=>{const value=prompt('New name',menu.dataset.name);if(value)api(`/api/v1/exports/${state.export}/entries`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({source:menu.dataset.menu,destination:join(state.path,value),overwrite:false})}).then(()=>{closeModal();load()}).catch(e=>toast(e.message))}},0)};
$('#breadcrumbs').onclick=e=>{const crumb=e.target.closest('[data-crumb]');if(crumb){state.path=crumb.dataset.crumb;load()}};
$('#new-folder').onclick=()=>{
  modal(`<h2>New folder</h2><label class="dialog-field">Folder name<input id="folder-name" autofocus></label><div class="dialog-actions"><button class="button secondary">Cancel</button><button type="button" id="create-folder" class="button primary">Create</button></div>`);
  $('#create-folder').onclick=async()=>{try{await api(`/api/v1/exports/${state.export}/directories`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({path:join(state.path,$('#folder-name').value)})});closeModal();await load()}catch(error){toast(error.message)}};
};
$('#upload').onchange=e=>upload(e.target.files);const drop=$('#drop-zone');['dragenter','dragover'].forEach(x=>drop.addEventListener(x,e=>{e.preventDefault();drop.classList.add('drag')}));['dragleave','drop'].forEach(x=>drop.addEventListener(x,e=>{e.preventDefault();drop.classList.remove('drag')}));drop.addEventListener('drop',e=>upload(e.dataTransfer.files));
$('#filter').oninput=render;document.querySelectorAll('th[data-sort]').forEach(th=>th.onclick=()=>{if(state.sort===th.dataset.sort)state.direction*=-1;else state.sort=th.dataset.sort;render()});
$('#connections').onclick=()=>modal(`<h2>Connect to ${escape(state.export)}</h2><p class="muted">Use your AdapterFS credentials for file protocols and separate access keys for S3.</p><h3>WebDAV</h3><div class="connection">${location.origin}/dav/${escape(state.export)}/</div><h3>SFTP</h3><div class="connection">sftp -P 2222 admin@${location.hostname}</div><h3>S3</h3><div class="connection">aws --endpoint-url http://${location.hostname}:9000 s3 ls</div><h3>FTP</h3><div class="connection">ftp://${location.hostname}:2121/${escape(state.export)}/</div>`);
function theme(value){document.documentElement.dataset.theme=value;localStorage.setItem('adapterfs-theme',value)}theme(localStorage.getItem('adapterfs-theme')||'light');$('#theme').onclick=()=>theme(document.documentElement.dataset.theme==='dark'?'light':'dark');$('#mobile-menu').onclick=()=>$('.sidebar').classList.toggle('open');
selectExport(exports[0]?.dataset.export);
})();
