<#ftl output_format="HTML">
<#import "template.ftl" as page>
<#import "password-commons.ftl" as passwordFields>
<@page.registrationLayout displayMessage=!messagesPerField.existsError("password","password-confirm"); section>
<#if section == "header">
  Alterar senha
<#elseif section == "form">
  <form id="kc-passwd-update-form" method="post" action="${url.loginAction}">
    <div><label for="password-new">Nova senha</label>
    <input id="password-new" name="password-new" type="password" autocomplete="new-password" required autofocus>
    <#if messagesPerField.existsError("password")><p class="auth-error" role="alert">${kcSanitize(messagesPerField.get("password"))?no_esc}</p></#if></div>
    <div><label for="password-confirm">Confirmar nova senha</label>
    <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password" required>
    <#if messagesPerField.existsError("password-confirm")><p class="auth-error" role="alert">${kcSanitize(messagesPerField.get("password-confirm"))?no_esc}</p></#if></div>
    <@passwordFields.logoutOtherSessions/>
    <div class="actions">
      <button type="submit">Salvar senha</button>
      <#if isAppInitiatedAction??><button class="secondary" type="submit" name="cancel-aia" value="true" formnovalidate>Cancelar</button></#if>
    </div>
  </form>
</#if>
</@page.registrationLayout>