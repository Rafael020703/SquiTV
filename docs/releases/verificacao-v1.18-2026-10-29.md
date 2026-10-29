# Squi TV v1.18 — verificação para publicação no GitHub

> Registro da verificação anterior à publicação. Consulte o estado atual e os arquivos públicos na [release v1.18](https://github.com/Rafael020703/SquiTV/releases/tag/v1.18).

Data: 29/10/2026. Base local e última publicação: `a772579`, v1.17, versionCode 18.

## Conclusão

**Candidata pronta do ponto de vista das verificações automáticas executadas.** O APK de produção está gerado, otimizado, assinado e separado com o nome esperado pelo atualizador. A publicação no GitHub ainda não foi realizada; os arquivos de fonte continuam com alterações locais sem commit.

A avaliação segue a instrução de usar verificações automáticas. Não há solicitação de teste manual ao usuário. Uso em aparelhos físicos e descoberta multicast real não são apresentados como comprovados.

## Resultado

| Critério | Resultado |
|---|---|
| Última release do GitHub | v1.17, versionCode 18; metadados consultados no repositório oficial. |
| Nova candidata | v1.18, versionCode 19. |
| Build de produção | `:app:assembleRelease` concluído, incluindo R8 e redução de recursos. |
| Lint de produção | `:app:lintRelease` concluído: zero Error/Fatal, 352 Warning e 4 Hint. |
| Testes | `:app:testDebugUnitTest`: 218 testes, zero falhas, erros ou ignorados. São 191 anteriores + 27 novas verificações. |
| APK de instrumentação | `:app:assembleDebugAndroidTest` compilado; não executado em dispositivo. |
| Configuração de cache | Entrada armazenada na compilação inicial e reutilizada nas verificações seguintes. |
| Identidade de produção | Pacote rsv.squitv; nome Squi TV; sem sufixo debug; manifest sem debuggable=true. |
| Compatibilidade de atualização | versionCode 19 superior ao 18 publicado e o mesmo certificado de assinatura. |
| Identidade do APK antigo | O SHA-256 da cópia local v1.17 coincide com o digest fornecido pelo GitHub para o asset publicado. |
| Assinatura do novo APK | `apksigner verify` válido, assinatura v2. |
| Alinhamento | `zipalign -c -P 16 4` válido; segmentos carregáveis das bibliotecas ELF arm64 inspecionadas alinhados a pelo menos 16 KiB. |
| Firebase no manifest final | Nenhum componente Firebase/FCM identificado. |
| FileProvider | Autoridade de produção rsv.squitv.fileprovider. |
| Materiais privados no Git | local.properties, keystores e APKs não rastreados. |
| Revisão de whitespace | `git diff --check` sem erros; apenas avisos de conversão LF/CRLF do checkout. |

O lint possui recomendações não bloqueantes, principalmente recursos não utilizados (261), uso de Log em vez de Timber (26), tipografia (15) e versões de dependências (10). O resultado não é descrito como ausência de avisos.

## Novas verificações automáticas

| Arquivo | Casos | O que foi exercitado |
|---|---:|---|
| SettingsBackupCodecTest | 12 | Round-trip, 25 opções, exclusões de senha/PIN, seleção parcial, valores vazios/falsos, compatibilidade v1, dados inválidos e limite de tamanho. |
| LocalTransferProtocolTest | 10 | Handshake TCP e impressão digital, cifragem nas duas direções, adulteração, repetição/reflexão de mensagens, sessão incorreta, limites e protocolo antigo. |
| LocalTransferApprovalTest | 3 | Duas instâncias do ViewModel comunicando via TCP loopback: esperar aceite, aplicar só a interseção permitida, confirmar conclusão, recusar e cancelar o receptor sem importar. O gerenciador NSD é simulado. |
| FavoritesScopeMigrationTest | 2 | Base sintética SQLite 25 reaberta pelo Room 26, preservação dos registros, normalização VOD/MOVIE, metadados, associação de legado uma única vez e isolamento por conta/perfil/tipo. |

Os ajustes necessários durante a criação dos testes se limitaram às próprias fixtures: fechamento explícito do Room e nomes curtos de arquivos/métodos para evitar MAX_PATH do SQLite nativo no Windows. Não foi necessário alterar a lógica de produção para obter os resultados acima.

A tarefa `:app:testReleaseUnitTest` foi consultada e não existe na configuração atual do AGP. Por isso, os resultados unitários apresentados são os de debug. O APK release foi compilado e inspecionado separadamente, incluindo R8; não se afirma que os testes unitários executam o DEX já minificado.

## Limites da evidência

- O ambiente não contém celular/TV conectado ou AVD disponível; descoberta NSD em um roteador real e interface em aparelhos físicos não foram exercitadas.
- O teste de migração usa uma base sintética de versão 25, não o banco particular de um usuário. Lacunas históricas anteriores à versão 21 continuam fora deste escopo.
- Não houve execução dos testes instrumentados, incluindo os testes de reprodução Media3 em aparelho.
- A aprovação na TV foi exercitada via API do ViewModel, não por cliques em uma interface renderizada.
- O protocolo sem código destina-se a uma rede local confiável. O detalhamento e as limitações de identidade dos anúncios NSD estão em [sincronização sem código](../sincronizacao-sem-codigo-2026-10-29.md).

## Arquivo de publicação

- [SquiTV-v1.18.apk](../../.artifacts/release-v1.18/SquiTV-v1.18.apk) — 6.90 MiB.
- [SHA-256](../../.artifacts/release-v1.18/SquiTV-v1.18.apk.sha256): `0621398C2CC093F4E6A1A859DC902953BE40B488C94DB0E5DD9B684F5EC52A1D`.
- Certificado SHA-256: `4C0CDDC8C53151C9EA88CB8C3632635AFAF5DFB25FB990A565E01838AE58F037`.
- [Notas prontas para a release](v1.18.md).
- [Resultado estruturado](../../.artifacts/release-v1.18/release-readiness.json).
- [Manifesto da fonte verificada](../../.artifacts/release-v1.18/source-manifest.sha256).

## Estado do GitHub

A [última release publicada](https://github.com/Rafael020703/SquiTV/releases/tag/v1.17) continua sendo a v1.17. A versão v1.18 foi preparada apenas localmente.

Para publicar a candidata verificada, o fluxo restante é revisar/incluir os arquivos de fonte e testes novos, salvar um commit, criar uma nova tag v1.18 sem substituir tags existentes e criar a release com o APK e seu checksum. Os arquivos privados de assinatura e a pasta .artifacts ficam fora do commit; o APK é um anexo da release.

O atualizador consulta releases/latest e busca um APK com prefixo SquiTV. O nome preparado é SquiTV-v1.18.apk; uma publicação marcada apenas como prerelease não será oferecida por esse endpoint. [Documentação da API de releases do GitHub](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

## Logs e referências

- [Build release e lint inicial](<C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/build-release-v1.18.log>)
- [Verificação final de release](<C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/final-release-v1.18.log>)
- [Testes e compilação de instrumentação](<C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/tests-release-v1.18.log>)
- [Consulta da tarefa release unitária indisponível](<C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/verify-release-v1.18.log>)
- [Relatório HTML dos testes](../../app/build/reports/tests/testDebugUnitTest/index.html)
- [Relatório HTML do lint](../../app/build/reports/lint-results-release.html)
- [Versionamento Android](https://developer.android.com/studio/publish/versioning)
- [Verificação de assinatura com apksigner](https://developer.android.com/tools/apksigner)
