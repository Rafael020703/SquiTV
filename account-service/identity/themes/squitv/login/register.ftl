<#ftl output_format="HTML">
<#import "template.ftl" as page>
<#import "user-profile-commons.ftl" as profileFields>
<#import "register-commons.ftl" as registration>
<@page.registrationLayout description="Escolha um usuário e uma senha." displayMessage=messagesPerField.exists("global"); section>
<#if section == "header">
  Criar conta
<#elseif section == "form">
  <form id="kc-register-form" method="post" action="${url.registrationAction}">
    <@profileFields.userProfileFormFields; callback, attribute>
      <#if callback == "afterField" && attribute.name == "username" && passwordRequired??>
        <div><label for="password">Senha</label>
        <input id="password" name="password" type="password" autocomplete="new-password" required>
        <#if messagesPerField.existsError("password")><p class="auth-error" role="alert">${kcSanitize(messagesPerField.get("password"))?no_esc}</p></#if></div>
        <div><label for="password-confirm">Confirmar senha</label>
        <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password" required>
        <#if messagesPerField.existsError("password-confirm")><p class="auth-error" role="alert">${kcSanitize(messagesPerField.get("password-confirm"))?no_esc}</p></#if></div>
      </#if>
    </@profileFields.userProfileFormFields>
    <@registration.termsAcceptance/>
    <button type="submit">Criar conta</button>
  </form>
  <p><a href="${url.loginUrl}">Já tenho conta</a></p>
</#if>
</@page.registrationLayout>