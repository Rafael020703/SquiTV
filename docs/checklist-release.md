# Checklist de release — Android e Windows

Preparação local não autoriza publicar. Commit/push, tags e distribuição exigem pedido específico do usuário. Não sobrescrever versões, tags ou assets existentes.

## 1. Código e verificações

- Conferir branch, status, diff, mudanças de outras tarefas e documentação do contrato.
- Rodar testes de shared, Android e gerador:
```powershell
python -B -m unittest discover -s tools/tests -v
.\gradlew.bat :shared:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest --console=plain
git diff --check
```
- R8/assinatura release devem usar a configuração existente. Não criar assinatura substituta.
- APK instrumentado compilado não comprova execução em TV/celular. Registrar o que rodou.

## 2. Numeração e APK real

- Para uma NOVA publicação autorizada, escolher tag inédita e incrementar versionName/versionCode acima do distribuído. Nunca reutilizar a versão/tag pública anterior. A v1.22 já foi distribuída com Android 1.22/code 23 e Windows 1.22.0; não reutilizar esses números para binários alterados.
- Conferir pacote rsv.squitv, SDK mínimo, ABIs e ausência de debuggable.
- Verificar assinatura com apksigner e comparar certificado com o APK anterior arquivado/distribuído.
- Gerar staging com tools/generate_manifest.py; nunca fornecer dados falsos para corresponder à tag.
- Rodar :shared:validateUpdateManifest com o arquivo produzido.
- Verificar hash/tamanho dos arquivos do staging. Material de assinatura e binários não entram no Git.

## 3. Compatibilidade e publicação futura

- Release latest para clientes Android antigos deve ter SquiTV-v<VERSAO>.apk correspondente à tag.
- Windows exclusivo não pode assumir latest enquanto esses clientes forem suportados. Nunca reaproveitar APK de versão antiga sob tag maior.
- A release conjunta terá um squitv-update.json com Android e Windows reais. O cliente Windows já existe; seu EXE real precisa ser incluído no manifesto conjunto antes de publicar.
- Criar draft e anexar todos os arquivos somente quando publicação for autorizada; validar antes de torná-la estável/visível.
- Após publicar, conferir API, flags, manifesto, URLs e digests dos assets reais. Não anexar manifesto de exemplo ou arquivo gerado para outro build.
- Usar SHA-256 por arquivo. Não inserir hash solto nas notas como fonte de seleção.

## 4. GitHub Actions

CI executa testes/compilações sem publicar nem acessar segredos de assinatura. Falta de assinatura em CI não autoriza gerar APK release com outro certificado. Execução local não comprova workflow remoto. Validar o workflow após push autorizado.

## 5. Windows — preparação de uma distribuição futura

- Usar JDK com jpackage e `:desktopApp:packageExe`; não distribuir a imagem debug/portable como atualização release.
- Preservar UUID, pasta/contexto de upgrades existentes e dados. Novas instalações Windows usam Program Files/per-machine; legados per-user usam transformação autenticada. Arquivar o instalador por hash fora de build antes de alternar variante.
- Windows online: `packageExe`/`writeUpdateMetadata` geram EXE com MSI/MST verificados; publicar somente o EXE canônico, checksum e manifesto. Não distribuir o MSI intermediário sem o bootstrapper de componentes.
- Verificar componentes/limpeza apenas com debug: `:desktopApp:packageExe -PdesktopVariant=debug -PdesktopInstaller=online` e `tools/verify-windows-installer.ps1`. Distinguir verificações isoladas de instalação real com UAC; nunca executar a release instalada para testes.
- Confirmar versão embutida, arquitetura real e nome canônico `SquiTV-<versionName>.exe`. Calcular tamanho/SHA-256 dos bytes definitivos; alterações posteriores invalidam o manifesto.
- generate_manifest.py continua sendo o gerador Android. Para staging Windows/conjunto, usar prepare_release.py e os metadados reais de :desktopApp:writeUpdateMetadata. O preparador não publica nem gera MSI.
- Validar o manifesto conjunto com :shared:validateUpdateManifest e conferir associação exata de tag, assets e digests publicados, respeitando clientes Android legados.
- Tela Configurações → Atualizações aparece em ambas as variantes; debug deve permanecer sem rede/download/instalação de produção.
- Compilação e empacotamento local não comprovam download/upgrade de uma instalação existente. Registrar separadamente essa execução quando realizada; não declarar atualização ponta a ponta antes de existir release Windows real.

## 6. Preparação local e changelogs separados

- Definir modo Android/Windows/conjunto ANTES de incrementar versões. Aplicar a tabela em contrato-atualizacoes.md; Windows exclusivo nunca assume Latest. Não reutilizar tags.
- Rever notas de cada plataforma, separando correções reais de extrações de código. Não anunciar ganho de desempenho sem medição ou paridade Windows completa.
- Gerar APK assinado e metadados EXE com a mesma revisão do código. Após empacotar Windows, preservar EXE/metadados fora de build antes de alternar debug/release.
- Exemplo da candidata conjunta, com JDK 21 contendo jpackage e assinatura Android já configurada:

```powershell
.\gradlew.bat :app:assembleRelease :desktopApp:writeUpdateMetadata --console=plain
python -B tools/prepare_release.py --android app/build/outputs/apk/release/app-release.apk --windows desktopApp/build/release/compose/binaries/main/exe/SquiTV-1.22.0.exe --windows-metadata desktopApp/build/release/update-metadata/windows-release.json --android-notes docs/releases/v1.22-android.md --windows-notes docs/releases/v1.22-windows.md --output-dir .artifacts/release-v1.22-candidate
.\gradlew.bat :shared:validateUpdateManifest '-Pmanifest=.artifacts/release-v1.22-candidate/upload/squitv-update.json' --console=plain
```

- Publicação somente Android omite --windows/--windows-metadata/--windows-notes; somente Windows omite --android/--android-notes. Tag é derivada do binário e modo; --tag opcional precisa concordar. Saída existente diferente é recusada.
- Revisar release-plan.json: tag, título, makeLatest, plataformas e lista exata de assets. upload/ contém somente binários/checksums/manifesto; release-notes.md é o corpo revisável do GitHub.
- Após autorização de publicação, criar draft, anexar somente upload/, revisar e publicar com o makeLatest definido no plano. Conferir os dados públicos antes de avançar referências.
- Somente depois publicar os candidatos channel-index nas referências updates/stable correspondentes. Não alterar a referência de uma plataforma que não recebeu versão.
- Aplicativos já instalados continuam com os leitores que foram distribuídos; publicar código novo não os atualiza sem APK/EXE de versão superior. Primeiro rollout deve preservar atualização dos Android anteriores e informar essa limitação.
