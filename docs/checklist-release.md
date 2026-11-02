# Checklist de release — Android e futuro Windows

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

- Para uma NOVA publicação autorizada, escolher tag inédita e incrementar versionName/versionCode acima do distribuído. A versão 1.20/code 21 usada na validação local não deve substituir a release pública 1.20.
- Conferir pacote rsv.squitv, SDK mínimo, ABIs e ausência de debuggable.
- Verificar assinatura com apksigner e comparar certificado com o APK anterior arquivado/distribuído.
- Gerar staging com tools/generate_manifest.py; nunca fornecer dados falsos para corresponder à tag.
- Rodar :shared:validateUpdateManifest com o arquivo produzido.
- Verificar hash/tamanho dos arquivos do staging. Material de assinatura e binários não entram no Git.

## 3. Compatibilidade e publicação futura

- Release latest para clientes Android antigos deve ter SquiTV-v<VERSAO>.apk correspondente à tag.
- Windows exclusivo não pode assumir latest enquanto esses clientes forem suportados. Nunca reaproveitar APK de versão antiga sob tag maior.
- A release conjunta terá um squitv-update.json com Android e Windows reais. O Windows será acrescentado quando implementado.
- Criar draft e anexar todos os arquivos somente quando publicação for autorizada; validar antes de torná-la estável/visível.
- Após publicar, conferir API, flags, manifesto, URLs e digests dos assets reais. Não anexar manifesto de exemplo ou arquivo gerado para outro build.
- Usar SHA-256 por arquivo. Não inserir hash solto nas notas como fonte de seleção.

## 4. GitHub Actions

CI executa testes/compilações sem publicar nem acessar segredos de assinatura. Falta de assinatura em CI não autoriza gerar APK release com outro certificado. Execução local não comprova workflow remoto. Validar o workflow após push autorizado.
