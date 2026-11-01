package rsv.squitv.data.pairing

/** Self-contained page: works on the LAN without fonts, CDNs or an installed phone app. */
internal object LocalPairingPage {
    fun render(session: PairingSession?): String = template
        .replace("__TOKEN__", escape(session?.token.orEmpty()))
        .replace("__CODE__", escape(session?.pairingCode.orEmpty()))
        .replace("__REMAINING__", ((session?.expirationTime ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L).toString())

    private fun escape(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;")

    private val template = """
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<title>Conectar à TV · Squi TV</title>
<style>
:root{color-scheme:dark;--bg:#080b10;--surface:#11161e;--raised:#1a222e;--line:#2a3442;--text:#edf2f7;--muted:#a6b2c2;--accent:#00d8ed;--error:#ff9292}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
button,input{font:inherit}button{cursor:pointer}button:disabled{cursor:default;opacity:.55}
:focus-visible{outline:3px solid var(--accent);outline-offset:4px}button,input{min-height:48px}
.shell{max-width:1040px;margin:auto;padding:40px 28px}.brand{display:flex;align-items:center;gap:12px;font-weight:750;font-size:23px;letter-spacing:-.6px}
.brand svg{width:48px;height:48px}.brand span{color:#ff9a36}.header{display:flex;justify-content:space-between;gap:20px;align-items:center}
.connection{font-size:12px;color:var(--muted);border:1px solid var(--line);border-radius:24px;padding:7px 12px;display:flex;gap:8px;align-items:center}
.dot{width:7px;height:7px;background:#72d5ad;border-radius:50%}.content{display:grid;grid-template-columns:1fr 420px;gap:64px;align-items:center;min-height:calc(100svh - 180px);padding:44px 0}
.eyebrow{font-size:12px;letter-spacing:1.8px;text-transform:uppercase;color:var(--accent);font-weight:650}
h1{font-size:clamp(30px,4.5vw,48px);line-height:1.13;letter-spacing:-1.6px;font-weight:700;margin:14px 0 18px;max-width:480px}
.lead{color:var(--muted);max-width:410px;margin:0;font-size:16px}.steps{margin:28px 0 0;padding:0;list-style:none;display:grid;gap:15px}
.steps li{display:flex;gap:12px;align-items:flex-start;color:var(--muted);font-size:14px}.step{display:grid;place-items:center;width:25px;height:25px;flex-shrink:0;border-radius:8px;background:var(--raised);color:var(--text);font-size:12px}
.card{background:var(--surface);border:1px solid var(--line);border-radius:22px;padding:28px}.card h2{font-size:22px;letter-spacing:-.6px;margin:0 0 5px}
.subtitle{font-size:14px;color:var(--muted);margin:0 0 22px}.session{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 15px;border:1px solid var(--line);border-radius:12px;margin-bottom:22px}
.session small{display:block;color:var(--muted);font-size:11px}.session strong{font-size:19px;font-variant-numeric:tabular-nums;letter-spacing:1px}.timer{font-size:12px;color:var(--muted);white-space:nowrap}
label{display:block;font-size:13px;font-weight:600;margin:17px 0 7px}input{width:100%;border:1px solid var(--line);border-radius:10px;background:#0b1017;color:var(--text);padding:12px 14px;outline-offset:2px;font-size:16px}
input:focus{border-color:var(--accent)}input::placeholder{color:#738194}.password{position:relative}.password input{padding-right:82px}.reveal{position:absolute;right:4px;top:0;min-width:74px;padding:0 10px;border:0;background:transparent;color:var(--muted);font-size:12px}
.primary{margin-top:24px;width:100%;border:0;border-radius:10px;background:var(--accent);color:#052026;font-weight:750;padding:12px 16px}.primary:hover:not(:disabled){background:#60e8f5}
#message{margin:16px 0 0;padding:12px 14px;border-radius:10px;font-size:14px;background:var(--raised);overflow-wrap:anywhere}
#message[data-kind=error]{color:var(--error);background:#321e26}#message[data-kind=success]{color:#a4edcc;background:#152d27}
[hidden]{display:none!important}.privacy{font-size:12px;color:var(--muted);margin:17px 0 0}.footer{border-top:1px solid var(--line);padding-top:18px;color:var(--muted);font-size:12px;display:flex;align-items:center;justify-content:space-between;gap:16px}
.address{display:flex;align-items:center;gap:12px;min-width:0}.address code{overflow-wrap:anywhere;font-size:12px}.copy{min-height:38px;padding:6px 11px;border:1px solid var(--line);border-radius:8px;background:transparent;color:var(--text);font-size:12px}.copy:focus-visible{outline-offset:2px}
#copyHelp{display:block;color:var(--muted);font-size:12px;margin-top:8px}.success-mark{width:54px;height:54px;display:grid;place-items:center;background:#173c31;color:#8de5bc;border-radius:50%;font-size:28px;margin:0 0 18px}
@media(max-width:820px){.content{grid-template-columns:1fr;gap:24px;max-width:480px;margin:auto;padding:36px 0}.intro h1{font-size:32px;max-width:none}.steps{display:none}.lead{font-size:14px}.card{padding:24px}.footer{max-width:480px;margin:auto;align-items:flex-start;flex-direction:column}.shell{padding:24px}.connection{font-size:11px}.brand{font-size:21px}}
@media(max-width:380px){.shell{padding:18px}.connection{border:0;padding:0}.card{padding:20px}.brand svg{width:36px;height:36px}}
@media(prefers-reduced-motion:no-preference){button{transition:background .15s}}
</style>
</head>
<body>
<div class="shell">
<header class="header"><div class="brand">
<svg viewBox="0 0 108 108" aria-hidden="true"><path d="M43 38 38 30M65 38 70 30" fill="none" stroke="#ff9a36" stroke-width="4" stroke-linecap="round"/><rect x="29" y="38" width="50" height="38" rx="10" fill="none" stroke="#ff9a36" stroke-width="4"/><path d="m49 46 17 11-17 11Z" fill="#ff6b35"/></svg>
<div>Squi <span>TV</span></div></div><div class="connection"><span class="dot"></span>Rede local</div></header>
<main class="content">
<section class="intro"><div class="eyebrow">Login à distância</div><h1>Seu acesso.<br>Na tela da TV.</h1>
<p class="lead">Digite os dados da sua conta aqui. O Squi TV recebe e verifica o acesso no aparelho que abriu esta página.</p>
<ol class="steps"><li><span class="step">1</span>Use a mesma rede da TV ou do aparelho de destino.</li><li><span class="step">2</span>Confira se o código ao lado é o mesmo da tela.</li><li><span class="step">3</span>Envie seus dados e acompanhe a confirmação.</li></ol></section>
<section class="card" aria-labelledby="formTitle">
<div class="success-mark" id="successMark" hidden aria-hidden="true">✓</div>
<h2 id="formTitle">Conectar ao Squi TV</h2><p class="subtitle" id="subtitle">Use o usuário e a senha da sua conta.</p>
<div class="session" id="session"><div><small>Código exibido no aparelho</small><strong id="pairCode">__CODE__</strong></div><span class="timer" id="timer" aria-label="Tempo restante"></span></div>
<form id="loginForm" method="post" action="/pair/login" data-remaining="__REMAINING__">
<input type="hidden" name="token" id="token" value="__TOKEN__">
<label for="username">Usuário</label><input id="username" name="username" autocomplete="username" autocapitalize="none" spellcheck="false" required maxlength="512" placeholder="Seu usuário" enterkeyhint="next">
<label for="password">Senha</label><div class="password"><input type="password" id="password" name="password" autocomplete="current-password" required maxlength="512" placeholder="Sua senha" enterkeyhint="go"><button type="button" class="reveal" id="reveal" aria-label="Mostrar senha" aria-pressed="false">Mostrar</button></div>
<button type="submit" id="submitBtn" class="primary">Conectar aparelho</button>
</form>
<p id="message" role="status" aria-live="polite" hidden></p>
<p class="privacy" id="privacy">Mantenha a tela de login aberta no aparelho. Use apenas uma rede de confiança.</p>
<noscript>Ative o JavaScript no navegador para enviar o login.</noscript>
</section>
</main>
<footer class="footer"><span>Conexão direta com o aparelho</span><div><div class="address"><code id="currentUrl"></code><button type="button" class="copy" id="copyBtn">Copiar endereço</button></div><span id="copyHelp" hidden></span></div></footer>
</div>
<script>
(function(){
'use strict';
var el=function(id){return document.getElementById(id);};
var form=el('loginForm'), submit=el('submitBtn'), pending=false, finished=false, expired=false;
var expires=Date.now()+Number(form.getAttribute('data-remaining')), pollTimer;
el('currentUrl').textContent=window.location.origin+window.location.pathname;
function message(text,kind){el('message').hidden=false;el('message').textContent=text;el('message').setAttribute('data-kind',kind||'');}
function expire(){if(finished||pending)return;expired=true;submit.disabled=true;submit.textContent='Sessão encerrada';message('Abra o login pela rede local no aparelho e acesse o novo endereço para tentar novamente.','error');clearInterval(pollTimer);}
function success(){finished=true;pending=false;clearInterval(pollTimer);form.hidden=true;el('session').hidden=true;el('privacy').hidden=true;el('successMark').hidden=false;el('formTitle').textContent='Aparelho conectado';el('subtitle').textContent='Continue na tela do Squi TV. Você já pode fechar esta página.';message('Login confirmado pelo aparelho.','success');el('password').value='';}
function tick(){if(finished)return;var remaining=Math.max(0,Math.ceil((expires-Date.now())/1000));el('timer').textContent=Math.floor(remaining/60)+':'+String(remaining%60).padStart(2,'0');if(remaining===0||!el('token').value)expire();}
el('reveal').addEventListener('click',function(){var showing=el('password').type==='password';el('password').type=showing?'text':'password';this.textContent=showing?'Ocultar':'Mostrar';this.setAttribute('aria-label',showing?'Ocultar senha':'Mostrar senha');this.setAttribute('aria-pressed',String(showing));});
el('copyBtn').addEventListener('click',async function(){
var button=this,text=el('currentUrl').textContent,copied=false;
try{if(navigator.clipboard&&window.isSecureContext){await navigator.clipboard.writeText(text);copied=true;}}catch(e){}
if(!copied){var field=document.createElement('textarea');field.value=text;field.style.position='fixed';field.style.opacity='0';document.body.appendChild(field);field.focus();field.select();try{copied=document.execCommand('copy');}catch(e){}field.remove();button.focus();}
if(copied){button.textContent='Copiado';el('copyHelp').hidden=true;setTimeout(function(){button.textContent='Copiar endereço';},1800);}
else{el('copyHelp').hidden=false;el('copyHelp').textContent='Selecione o endereço acima para copiar manualmente.';var range=document.createRange();range.selectNodeContents(el('currentUrl'));window.getSelection().removeAllRanges();window.getSelection().addRange(range);}
});
async function poll(){if(pending||finished||expired||document.hidden)return;try{var response=await fetch('/pair/status?token='+encodeURIComponent(el('token').value),{cache:'no-store'});if(!response.ok)return;var data=await response.json();if(data.status==='SUCCESS')success();else if(data.status==='EXPIRED')expire();}catch(e){}}
form.addEventListener('submit',async function(event){
event.preventDefault();if(pending||finished||expired)return;if(!form.reportValidity())return;
var body=new URLSearchParams(new FormData(form));pending=true;submit.disabled=true;submit.textContent='Verificando acesso…';message('O aparelho está verificando seus dados. Aguarde.');
var controller=window.AbortController?new AbortController():null;
var timeout=setTimeout(function(){if(controller)controller.abort();},50000);
try{
var response=await fetch('/pair/login',{method:'POST',body:body,signal:controller?controller.signal:undefined,cache:'no-store'});
var data=await response.json();
if(response.ok&&data.success){success();return;}
pending=false;message(data.error||'Não foi possível entrar. Confira os dados e tente novamente.','error');
if(response.status===410||response.status===403){expire();}else{submit.disabled=false;submit.textContent='Tentar novamente';el('password').focus();}
}catch(e){pending=false;submit.disabled=false;submit.textContent='Tentar novamente';message('Não foi possível confirmar a conexão. Confira a tela do aparelho e a rede antes de tentar novamente.','error');}
finally{clearTimeout(timeout);pending=false;if(!finished)tick();}
});
tick();setInterval(tick,1000);pollTimer=setInterval(poll,2500);poll();
})();
</script>
</body>
</html>
    """.trimIndent()
}
