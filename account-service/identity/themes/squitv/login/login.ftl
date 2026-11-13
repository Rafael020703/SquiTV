<#ftl output_format="HTML">
<#import "template.ftl" as page>
<@page.registrationLayout description="Use o usuário e a senha da sua Conta Squi TV." displayMessage=!messagesPerField.existsError("username","password") displayInfo=realm.registrationAllowed && !registrationDisabled??; section>
<#if section == "header">
  Entrar
<#elseif section == "form">
  <form method="post" action="${url.loginAction}">
    <#if usernameHidden??>
      <#if auth?has_content && auth.attemptedUsername?has_content><p>${auth.attemptedUsername}</p></#if>
    <#else>
      <div><label for="username">Usuário</label>
      <input id="username" name="username" autocomplete="username" value="${(login.username!'')}" required autofocus></div>
    </#if>
    <div><label for="password">Senha</label>
    <input id="password" name="password" type="password" autocomplete="current-password" required <#if usernameHidden??>autofocus</#if>></div>
    <#if messagesPerField.existsError("username","password")><p class="auth-error" role="alert">${kcSanitize(messagesPerField.getFirstError("username","password"))?no_esc}</p></#if>
    <input type="hidden" name="credentialId" value="${(auth.selectedCredential!'')}">
    <button type="submit">Entrar</button>
  </form>
<#elseif section == "info">
  <a href="${url.registrationUrl}">Criar conta</a>
</#if>
</@page.registrationLayout>