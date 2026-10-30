# Verificação da release v1.19 — 30/10/2026

Base: v1.18, commit dcf9848. Versão candidata: **1.19 / versionCode 20**.

## Resultado

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease --console=plain
```

**BUILD SUCCESSFUL em 3m 38s**, 134 tarefas (50 executadas, 84 atualizadas). Configuration Cache armazenado.

| Verificação | Resultado |
| --- | --- |
| Testes JVM/Robolectric | 241 executados, 241 aprovados; 0 falhas, erros ou ignorados |
| Regressões novas | 23 testes: busca, vínculo FTS, notas da atualização, navegação externa, download HTTP e identidade do APK |
| Testes instrumentados | APK compilado; execução não realizada por ausência de dispositivo conectado |
| Release | R8 e redução de recursos concluídos |
| Lint | 0 erros/fatais; 345 avisos e 4 sugestões |
| Pacote | rsv.squitv, sem debuggable |
| Versão no APK | 1.19 / 20 |
| Android mínimo / ABIs | API 24 / arm64-v8a, armeabi-v7a |
| Assinatura | APK v2 válida; certificado idêntico ao APK publicado da v1.18 |
| Alinhamento | zipalign com páginas de 16 KiB aprovado; segmentos LOAD das bibliotecas ARM64 com alinhamento >= 16 KiB |
| Bibliotecas nativas | Mesmos arquivos e mesmos bytes do APK publicado da v1.18 |
| Tamanho | 7272269 bytes |
| SHA-256 | 605c00e16a0c9c715a3dad42dbd9f983eafdd50088e80541e6436df69efeed1e |
| Certificado SHA-256 | 4c0cddc8c53151c9ea88cb8c3632635afaf5dfb25fb990a565e01838ae58f037 |
| Diff e arquivos locais | git diff --check aprovado; chaves, propriedades locais e APKs fora do índice |

## Correções na preparação

- A busca preenchida acessava estados antes da inicialização. A inicialização foi movida para depois da criação dos estados.
- A busca cancela a tarefa anterior, aguarda 250 ms na digitação, salva o texto e impede a publicação de resultados antigos, inclusive após limpar o campo.
- O índice FTS passou a se relacionar por rowid, evitando duplicação quando existem conteúdos com nomes iguais. Regressões exercitam Room e SQLite reais no ambiente Robolectric.

## Limites da verificação

Não foram executados cenários em TV, celular ou emulador Android. Não há comprovação visual em aparelho, desempenho medido ou conclusão do instalador Android real. Os testes de identidade de APK simulam metadados do PackageManager; a assinatura criptográfica do artefato publicado foi verificada separadamente pelo apksigner.

Histórico/downloads por conta e perfil, paginação e isolamento do fixture instrumentado continuam no relatório de prioridades. Esta release mantém o esquema Room 26 e não aplica migrações nessas áreas.

## Evidências locais

- Log: C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/verify-v1.19.log
- APK, hash, verificações e manifesto dos arquivos fonte: .artifacts/release-v1.19/
- Hash do manifesto de entradas da build: 0e36f6be8fc6256642429851dd724a871f9f6b27c274bb99d6725366d403c2f5

Publicação autorizada pelo usuário após a verificação automática. Tag da release: v1.19. Página: https://github.com/Rafael020703/SquiTV/releases/tag/v1.19.
