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

## 3. Windows

Usar JDK 21 completo com `jpackage`; o JBR reduzido do Android Studio pode não conter essa ferramenta. Consultar `windows.md` para comandos, variantes e caminhos. Instaladores e dados locais são ignorados pelo Git.

Para acompanhar alterações sem reinstalar:

```powershell
.\gradlew.bat :desktopApp:createDistributable '-PdesktopVariant=debug' --console=plain
.\tools\deploy-windows-debug.ps1
```

O deploy atualiza o atalho da debug sem iniciar/encerrar processos ou alterar a release instalada. Para empacotar produção, use `:desktopApp:writeUpdateMetadata -PdesktopVariant=release`; isso não autoriza executar o instalador.

## 4. Documentação e arquivos locais

Atualize README, arquitetura e guia Windows quando o comportamento mudar. Novidades públicas ficam em `docs/releases/` com notas separadas por plataforma. Planos Gemini, auditorias e `docs/manutencao-*.md` permanecem locais conforme o `.gitignore`; não desapareceram só por não entrarem no commit.

Na distribuição, fontes, recursos, Gradle Wrapper, schemas Room, scripts e documentação permanente vão ao Git. APK/EXE/checksums/manifesto vão aos assets da release. Dados locais, credenciais, chaves de assinatura, caches e diretórios de staging nunca devem ser adicionados. Conferir arquivos já rastreados: `.gitignore` sozinho não os remove.
