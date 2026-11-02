# Contrato de atualizações — Squi TV

Schema v1, canal estável. Fonte de verdade: modelos e validador em `shared/src/main/kotlin/rsv/squitv/shared/update`.

## Manifesto por release

Cada release pode conter um único asset chamado `squitv-update.json`, incluindo artefatos Android e Windows. Não é um feed gravado no código-fonte: é gerado a partir dos binários reais e anexado à release quando a publicação for autorizada.

Campos do documento: `schemaVersion=1`, `productId="squi-tv"`, `channel="stable"`, `releaseTag`, `artifacts` e o campo opcional `changelogs` (mapa Android/Windows).

Campos de cada artefato: `platform`, `kind`, `architecture`, `assetName`, `versionName`, `sizeBytes`, `sha256`. Nomes são exatos e únicos, sem diretórios. Hash é hexadecimal SHA-256 de 64 caracteres. Tamanho é positivo. Limite: 16 artefatos e 256 KiB de manifesto.

| Plataforma | Campos e regras |
| --- | --- |
| Android | `platform=android`, `kind=apk`, `applicationId=rsv.squitv`, `versionCode` positivo até 2.100.000.000, `minSdk` positivo e `abis` reais do APK. Debug não pode entrar. |
| Windows | Schema aceita `kind=exe` ou `msi`, arquitetura `x64` ou `arm64`, sem campos Android. O cliente Windows atual seleciona somente `kind=exe` com nome exato `SquiTV-<versionName>.exe`, versão superior e arquitetura correspondente. Debug e portable não são instaladores elegíveis. O build atual é x64. |

Android: uma ABI usa seu nome como arquitetura; várias usam `universal` com lista explícita; APK sem bibliotecas nativas usa `any` e lista vazia. `universal` não significa suporte a todas as CPUs. `all` não é válido. ABIs suportadas nesta versão do contrato: arm64-v8a, armeabi-v7a, x86 e x86_64. A seleção exige interseção com as ABIs declaradas pelo aparelho, sem inferência de 32/64 bits.

## Versões

Versões estáveis numéricas de um a quatro componentes, sem zeros à esquerda, beta, texto residual ou overflow de inteiro de 32 bits por componente. Prefixo v/V é aceito ao comparar tags. `1.20` equivale a `1.20.0`; `1.10` é superior a `1.9`. A versão do artefato não pode exceder a versão global da release.

No Android, o código deve ser superior ao instalado e o nome de versão não pode ser inferior ao instalado. Comparação é numérica. O código anunciado também é conferido no APK baixado, antes da instalação, juntamente com pacote, nome e certificado/rotação compatível.

Uma release com versão global nova e somente Windows novo não deve anunciar atualização Android se seu artefato Android não mudou.

## Associação e transporte

- Manifesto deve declarar exatamente a tag da release.
- Todos os artefatos declarados precisam existir na mesma release, com nome e tamanho iguais.
- URL inicial deve ser HTTPS em github.com:443, sem credenciais/query/fragment, no caminho exato `/<owner>/<repo>/releases/download/<tag>/<asset>`.
- O cliente dedicado de atualização não usa o interceptor do servidor IPTV. Exige HTTPS em cada salto e não permite downgrade de HTTPS para HTTP. CDNs oficiais continuam utilizáveis por redirects HTTPS.
- Testes substituem somente origem e certificado por um MockWebServer HTTPS local. Não há bypass HTTP em produção.
- Digest do asset, manifesto e sidecar, quando disponíveis juntos, devem concordar. Sidecar é `<asset>.sha256`; contém um hash isolado ou uma única linha com hash e nome exato do arquivo. Nunca extrair o primeiro hash das notas.
- Downloads de metadados têm limite de tamanho e cancelamento até o fim da leitura do corpo. Resposta vazia, inválida, HTTP malsucedido, schema desconhecido, tamanho/hash conflitante ou referência inexistente resulta em erro, sem fallback na mesma release.

## Descoberta e cache

O resolver consulta até duas páginas de 30 releases e processa até 20 manifestos relevantes. Se o histórico estiver cheio ou houver mais de 20 manifestos, tenta a referência fixa da própria plataforma em `updates/stable/android.json` ou `windows.json` na branch padrão, via GitHub Contents API com Accept raw. A referência aponta uma tag publicada e o hash do seu manifesto. Com referência válida, resolve diretamente essa release, sem percorrer o histórico de outra plataforma. Somente 404 representa referência ausente; falhas HTTP, timeout ou referência inválida são erros e não são cacheados. Exclui draft/pré-release, ignora releases anteriores à versão instalada e compara candidatas Android independentemente da ordem de publicação.

Sem candidata e com histórico truncado, retorna erro de descoberta incompleta. Manifesto válido Windows exclusivo permite continuar a busca. Manifesto presente inválido não equivale a ausência.

Só resultados `UpToDate` e `UpdateAvailable` são cacheados por 12 horas na sessão. Erro limpa o sucesso anterior e permite nova tentativa sem `force`. Consulta forçada ignora cache, mas nunca o bloqueio debug. Nenhum token privado é embutido no app.

No Android, o fallback de repositório só ocorre em 404 da primeira consulta primária. Falha na segunda página, 403, 429 ou indisponibilidade não troca para o repositório legado.

## Releases antigas e clientes já distribuídos

Releases sem manifesto usam o APK único de nome `SquiTV-v<VERSAO>.apk`. Arquivos ambíguos são recusados. Digest/sidecar são associados ao arquivo; uma release histórica realmente sem ambos preserva o fluxo legado, mas pacote, versão e assinatura do APK continuam sendo conferidos.

Clientes antigos não conhecem este resolver. Enquanto forem suportados:
- A release consumida por `/releases/latest` precisa conter o APK Android correspondente à tag.
- Não colocar release Windows exclusiva em latest; usar pré-release durante o desenvolvimento ou publicação explicitamente não-latest, conferindo a API.
- Não copiar APK antigo para tag nova: isso cria ofertas repetidas no cliente antigo.
- Não alterar tags/arquivos de releases existentes.
- A v1.21 Android já introduziu o resolver multiplataforma; novas releases continuam preservando o caminho legado para aparelhos que ainda não a instalaram.

## Geração e validação

O script `tools/generate_manifest.py` lê versão/pacote/SDK via aapt2, lista ABIs do ZIP, verifica assinatura com apksigner e calcula tamanho/hash dos bytes reais. Não recebe versões manuais para substituir metadados. Produz uma pasta de staging com APK de nome compatível com o legado, checksum e manifesto; não sobrescreve arquivos diferentes.

Exemplo local, substituindo a tag pela versão REAL do APK:

```powershell
$env:JAVA_HOME = 'G:\Program Files\Android\Android Studio\jbr'
python -B tools/generate_manifest.py app/build/outputs/apk/release/app-release.apk --tag v1.20 --output-dir .artifacts/android-update-staging
.\gradlew.bat :shared:validateUpdateManifest '-Pmanifest=.artifacts/android-update-staging/squitv-update.json' --console=plain
```

A tag precisa corresponder ao APK por padrão. `--not-latest` permite explicitamente um Android anterior em release global posterior que NÃO será anunciada como latest para clientes antigos. Não é permissão de publicação.

O cliente Windows consome o contrato. `tools/prepare_release.py` compõe o staging Android, Windows ou conjunto; reaproveita o gerador assinado Android e exige metadados do Windows gerados por `:desktopApp:writeUpdateMetadata`. Esse registro vincula versão, arquitetura do runtime, nome, tamanho e hash ao EXE real. O preparador confere também ProductName/ProductVersion do instalador. Toda saída é local, sem publicação. Não publicar dois arquivos de manifesto separados na mesma release.

Referências: [GitHub Releases](https://docs.github.com/en/rest/releases/releases), [AAPT2](https://developer.android.com/tools/aapt2), [OkHttp TLS de testes](https://github.com/square/okhttp/tree/master/okhttp-tls).

## Cliente Windows atual

A tela Atualizações é visível em debug e release. Operações de rede, download e instalação de produção ficam desabilitadas na debug, inclusive consultas forçadas. Release usa WindowsUpdateRepository com cliente HTTPS dedicado e sem credenciais IPTV. Sem manifesto, uma release somente Android é ignorada; não há fallback para APK. Manifesto presente inválido produz erro. Instalação exige clique explícito após conferir os bytes baixados; não é uma execução automática ao encontrar versão. A atualização real ainda não foi exercitada contra uma release Windows pública nesta etapa.

## Changelogs por plataforma

`changelogs` é um mapa opcional: `{"android": "novidades Android", "windows": "novidades Windows"}`. Cada texto possui no máximo 20.000 caracteres e sua plataforma precisa existir em artifacts. Não há campo com novidades globais implícitas. Publicações novas exigem notas não vazias de cada plataforma incluída. Clientes novos exibem somente a chave da própria plataforma. Se o mapa estiver presente mas não contiver a plataforma, não exibem as notas da outra. Releases antigas sem esse campo e com uma única plataforma mantêm body como fallback; manifestos antigos conjuntos não têm seu body misturado exibido como changelog exclusivo.

Mantido schemaVersion=1 porque o campo é opcional e leitores distribuídos ignoram campos desconhecidos. Isso não faz leitores antigos filtrarem changelogs: seu comportamento anterior permanece. O corpo no GitHub identifica claramente Android e Windows e cada uma de suas seções.

## Numeração aprovada e tags únicas

| Publicação | Versão do aplicativo | Tag GitHub | Latest |
| --- | --- | --- | --- |
| Somente Android | Ex.: 1.22.1 | v1.22.1 | Sim, preservando caminho legado Android |
| Somente Windows | Ex.: 1.22.1 | v1.22.1.1 | Não |
| Conjunta | Android 1.23 / Windows 1.23.0 | v1.23 | Sim |

Android e Windows podem ter a mesma versão de correção, sem atualizar a outra plataforma. Git tags são únicas no repositório; a quarta componente `.1` é reservada para a tag técnica da publicação Windows, mantendo versionName, arquivo e título Windows em três componentes. Não usar prefixo windows-v/android-v enquanto Android v1.21 distribuído validar tags apenas numéricas. Todas as versões declaradas continuam menores ou iguais à tag. Conjuntas usam uma nova versão menor equivalente nos dois aplicativos; não reutilizar número/tag/asset já publicado. Android sempre incrementa versionCode quando recebe atualização; esse número não existe no Windows.

## Referência estável por plataforma

Schema da referência: schemaVersion=1, productId=squi-tv, channel=stable, platform, versionName, releaseTag e manifestSha256. Limite de leitura: 4 KiB. Sem URL arbitrária. A referência é validada contra plataforma/versão/tag, hash exato e conteúdo do manifesto, que deve conter aquela versão/plataforma. Os arquivos de assets e manifesto continuam sendo os da release imutável.

O staging produz candidatos em `channel-index/<platform>.json`, que NÃO são assets a anexar e NÃO devem ser copiados para o código antes da publicação real. Após publicar/verificar assets, atualizar somente `updates/stable/<platform>.json` da plataforma alterada. Não modificar a referência da outra plataforma em publicação isolada. A política depende dessa atualização ser concluída; índice desatualizado pode manter um cliente antigo sem a oferta mais recente quando a busca histórica estiver esgotada.
