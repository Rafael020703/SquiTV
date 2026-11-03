# Arquitetura e Separação de Plataformas — Squi TV

Este documento descreve a divisão de responsabilidades entre o aplicativo Android (`:app`), o núcleo compartilhado (`:shared`) e o aplicativo Desktop Windows em desenvolvimento (`:desktopApp`).

## 1. Módulos do Repositório

* **`:app`**: Aplicação Android nativa. Contém UI em Jetpack Compose, ExoPlayer (Media3), Room Database, WorkManager, serviços em foreground, integrações com Android TV/Mobile e adaptadores de atualização específicos do Android.
* **`:shared`**: Núcleo puro Kotlin/JVM, sem Android. Contém versões, contrato de atualização, modelos/parser Xtream, DNS, cores, montagem do endereço de canais, índice de navegação circular e PlayerOverlayController/PlayerSeekController extraídos do Android, DTOs/configuração pública GitHub e filtro de novidades releaseHighlights. O Android consome as regras extraídas sem migrar seu banco/login.
* **`:desktopApp`**: Compose Desktop Windows x64. Login, dashboard, catálogos, detalhes/temporadas/episódios, favoritos, histórico/retomada, modos de categorias, preparação de metadados e Multi-view. HTTP cancelável, DPAPI, persistência atômica/validada e LibVLC/JNA são adaptadores Windows. DTOs/serializers Xtream, agrupamento de qualidades, projeção das categorias e regras de progresso vêm do Android em `:shared`, mantendo os pacotes públicos. Atualizações possui descoberta/download/instalação explícita e tela nas Configurações. Downloads, perfis/sincronização Android e cache offline completo de favoritos continuam pendentes; consultar `windows.md` para os limites.
* **`buildSrc`**: Tarefas de build e desenho do ícone compartilhado por Android/Windows. Prepara o runtime nativo Windows com hash fixado; arquivos baixados/gerados não pertencem ao Git nem ao núcleo de execução compartilhado.
* **`uiCommon/src/main/kotlin`**: Fontes visuais Compose puras compiladas por Android e Windows. Contém cartões da dashboard, controles/menus do player, favorito compacto, grade/seletor de categorias do Multi-view, apresentação dos modos de tela, banner compacto e painéis de atualizações. Não é um módulo Gradle nem contém APIs Android. `:shared` continua sem Compose, e desktop não depende de `:app`. O Gradle das duas aplicações inclui explicitamente esse diretório de fontes.

## 2. Invariantes Arquiteturais

1. **`shared` nunca depende de aplicações**: O fluxo de dependência é estritamente unidirecional (`app -> shared`, `desktopApp -> shared`).
2. **Separação de Plataforma**: Players de vídeo, armazenamento local seguro, notificações e permissões de sistema são responsabilidade exclusiva de cada plataforma.
3. **Contrato de Atualização Único**: A lógica de parsing de versões, validação de manifesto e seleção de artefato é centralizada em `:shared` para garantir comportamento idêntico no Android e no Windows.

## 3. Metadados do aplicativo instalado e testes de atualização

`UpdateModule` fornece `InstalledAppInfo` ao `UpdateRepository` por Hilt. Em produção, os dados vêm de `AppVersionProvider`, `BuildConfig`, pacote e capacidades reais do Android. Os testes fornecem uma fixture release explícita, sem modificar o pacote ou `BuildConfig` do projeto.

`force` ignora somente o cache/intervalo de consulta; não permite que uma variante debug consulte atualizações release. A proteção de debug é verificada antes da consulta de rede, inclusive quando `force=true`.

Para artefatos Android, `shared` exige interseção entre as ABIs reais do APK e as informadas pelo aparelho. Ser um dispositivo de 64 bits não comprova suporte a aplicativos de 32 bits nem compatibilidade entre ARM e x86. `universal` requer lista explícita; `all` é inválido. `any` se aplica somente a APKs sem bibliotecas nativas.

## 4. Distribuição compartilhada

O cliente de atualização tem qualificador próprio `UpdateOkHttpClient` e política HTTPS própria, sem reescrita de URLs pelo provedor IPTV. `UpdateUrlPolicy` valida a associação inicial entre repositório, tag e asset. Testes usam certificado/origem HTTPS locais; produção não permite HTTP.

Modelos, configuração pública GitHub e seleção por plataforma são compartilhados. Transporte, versão instalada e instalação são adaptadores: Android valida APK/pacote/certificado; Windows confere EXE/tamanho/hash e abre seu instalador por ação explícita. A debug Windows mantém operações de produção desabilitadas. O gerador extrai os dados do APK real e `:shared:validateUpdateManifest` valida a saída com o contrato dos clientes. O Windows consome o mesmo contrato, sem instalar APK ou depender de :app; releases públicas já fornecem seus assets/manifesto. Isso não comprova upgrade local em execução. O gerador Android permanece dedicado ao APK; prepare_release.py compõe staging multiplataforma com changelogs separados e referências por plataforma a partir dos binários finais. As referências avançam somente depois da publicação e conferência dos assets.

Consultar `contrato-atualizacoes.md` e `checklist-release.md` antes de mudar schema, versões ou publicação. Clientes legados ainda dependem do APK na release latest; publicar Windows exclusivo sem respeitar a transição rompe esse caminho.
