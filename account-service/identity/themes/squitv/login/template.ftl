<#ftl output_format="HTML">
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false description="">
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="referrer" content="no-referrer">
  <title>Conta Squi TV</title>
  <link rel="stylesheet" href="/auth.css">
</head>
<body>
  <main class="auth-shell" id="squitv-auth">
    <div class="auth-brand-panel">
      <a class="auth-brand" href="/" aria-label="Squi TV, início">
        <span class="auth-mark" aria-hidden="true">▶</span><span>Squi TV</span>
      </a>
    </div>
    <section class="auth-card" aria-labelledby="auth-title">
      <h1 id="auth-title"><#nested "header"></h1>
      <#if description?has_content><p class="auth-description">${description}</p></#if>
      <#if displayMessage && message?has_content && message.summary?has_content>
        <p class="auth-message" role="status">${kcSanitize(message.summary)?no_esc}</p>
      </#if>
      <#nested "form">
      <#if displayInfo><div class="auth-footer"><#nested "info"></div></#if>
    </section>
  </main>
</body>
</html>
</#macro>