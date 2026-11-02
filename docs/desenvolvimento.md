# Guia de Desenvolvimento — Squi TV

## 1. Comandos de Build e Teste

Para compilar e verificar o projeto localmente (incluindo o núcleo compartilhado `:shared` e a aplicação Android `:app`):

```powershell
.\gradlew.bat :shared:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
git diff --check
```

Para o gerador: `python -B -m unittest discover -s tools/tests -v`. Para uma validação completa local de distribuição, incluir `:app:assembleRelease` com a assinatura existente e seguir `checklist-release.md`. Não publicar a partir desses comandos.

A task `:shared:validateUpdateManifest -Pmanifest=<arquivo>` verifica manifestos gerados com o contrato usado pelos clientes. O script e suas dependências Android SDK são descritos em `contrato-atualizacoes.md`.

## 2. Padrões de Código
* **Kotlin / Compose**: Utilizar idioms modernos do Kotlin e Jetpack Compose (Material 3).
* **Isolamento de Plataforma**: Mantenha qualquer código específico de Android em `:app` e mantenha `:shared` totalmente puro (Kotlin/JVM sem referências a `android.*`).
