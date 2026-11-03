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

## 5. Arte do launcher em TVs — desenvolvimento local

`GenerateLauncherIcons` preserva os ícones originais do aplicativo, incluindo os recursos adaptativos. `TvLauncherArtwork` gera somente um banner opaco 1280×720 em `drawable-television-nodpi`, reutilizando a marca original. O banner atende aos launchers que consultam `android:banner`; não substitui o ícone da aplicação. Não há aliases ou novas entradas no launcher.

As fotos enviadas pelo usuário no Fire TV 4K / Fire OS 8.1.8.5, com instalação por APK, mostraram que as três tentativas não produziram um cartão escuro completo: R1 manteve laterais cinzas; a transparência da R2 expôs um fundo cinza rejeitado; R3 normalizou o ícone horizontal para uma área quadrada e deixou a marca muito pequena. Portanto, os ícones específicos de TV e a propriedade experimental `fireTvWideIcon` foram retirados. O aplicativo voltou ao ícone original, mais legível nas fotos, sem alegar que isso remove as laterais.

```powershell
.\gradlew.bat :app:assembleDebug :app:compileReleaseKotlin --console=plain
```

O APK debug usa `rsv.squitv.debug`, pode coexistir com a release e não altera seus dados. PNGs são gerados pelo build; não editar imagens dentro do APK assinado. Esta etapa não altera versões nem autoriza publicação.

A inspeção dos recursos e a compilação não comprovam a aparência no launcher Amazon. Não foi encontrada uma correção comprovada no APK para preencher o cartão desse aparelho. O formato de arte solicitado para a Amazon Appstore não garante o mesmo tratamento em instalação por APK. Não recomendar apagar dados, desinstalar a release ou instalar launchers auxiliares para forçar o ícone.

Referências: [recursos alternativos Android](https://developer.android.com/guide/topics/resources/providing-resources), [ícone da atividade](https://developer.android.com/guide/topics/manifest/activity-element#icon) e [artes Fire TV](https://developer.amazon.com/docs/app-submission/appstore-details.html).

## Controles e posição de catálogo — alterações locais de 03/11/2026

Ao vivo e VOD compartilham `PlayerVolume` e `PlayerVolumeNotice`: controles ocultos usam esquerda/direita para volume do app, 5% por tecla solta, com indicador de 1,5 s. Controles abertos mantêm navegação/seek. Android usa `Player.volume` (0–1) com checagem dos comandos disponíveis; Windows usa LibVLC e a preferência de volume existente. Não altera CEC ou volume físico da TV. O suporte efetivo do caminho de áudio do aparelho ainda não foi observado. Referência: [volume do Media3](https://developer.android.com/reference/androidx/media3/common/Player#setVolume(float)).

`CatalogBrowseMemory` em `:shared` guarda IDs de categoria/conteúdo por escopo e tipo. Android usa conta/perfil; Windows usa conta e arquivo da variante. Há memória de itens por categoria e validação da seleção contra as categorias visíveis carregadas, com a mesma regra Android de fallback Servidor/Filtros. Foco é salvo após 400 ms; cliques salvam imediatamente; gravações iguais são ignoradas. Limites: 64 escopos/tipos, 16 categorias por escopo e 16 KiB totais, com descarte de memória de navegação antiga quando necessário. Não armazena o catálogo, URL ou credenciais. Preferências globais legadas Android são preservadas, sem atribuí-las automaticamente a uma conta/perfil sem identidade comprovada.

No Windows, setas usam navegação de foco após o componente focado poder consumir a tecla; Enter e Esc mantêm as ações existentes. Campos de texto, sliders, menus e atalhos do player têm prioridade. O adaptador também está nos HUDs e diálogos; não modifica tela cheia. Compilar não substitui a observação dos percursos de teclado em execução.


## Capas/prévias e episódios — ajustes locais de 03/11/2026

O pedido de preview refere-se a imagens dentro do app, não a @Preview do Android Studio. Shared `ContentImages` normaliza arte do provedor sem inferir CDN; `ImageTextSerializer`/`ImageListSerializer` tornam esses campos opcionais tolerantes, sem mudar o formato dos DTOs. Android usa `ContentImageInterceptor` e `ContentImageSource` para tentar alternativas conhecidas; o cache Coil usa a URL resolvida. Recursos/arquivos/URIs locais seguem seu fluxo original. URLs relativas consultam o servidor atual fora da thread principal.

Windows mantém quatro slots/cache limitado e passa a resolver redirecionamentos de imagem e decodificar WebP/SVG pelo Skia já embarcado. Não há nova dependência. Mantidos cancelamento, limites de resposta/dimensões, HTTPS e a separação dos clientes de imagem, IPTV, download e atualização. Uma imagem indisponível não é marcada como uma arte carregada; placeholders e retentativa visível limitada são explícitos.

Downloads Windows agora distinguem MOVIE/EPISODE e conservam os registros antigos como filmes por valor padrão. Temporadas são filas manuais, um arquivo por vez, canceladas/interrompidas no fechamento/logout/erro. O endpoint continua sendo construído por OnDemandPlayback; sem credenciais/URLs no índice. Reprodução de séries locais usa somente arquivos completos verificados da conta/série; sem fallback de rede. Android já possuía download de episódio/temporada e não teve motor/índice de downloads alterado nesta etapa.

Verificações: assembleDebug Android, compileReleaseKotlin Android, createDistributable Windows debug e diff --check. Detalhes/evidências no relatório local docs/revisao-previews-series-downloads-2026-11-03.md e .artifacts/previews-series-downloads-2026-11-03/verification.json. Nenhum teste novo/executado, imagem real/provedor/decodificador/player observado ou release instalada alterada.

## Reprodução e histórico — v1.24

WindowsLivePlayer associa mídia, polling e comandos pendentes à geração do pedido, reduz JNI em estados terminais e mantém operações nativas fora dos updates repetíveis do StateFlow. DesktopWatchHistory elimina a revalidação completa para amostras idênticas. No Android, SyncWatchProgressUseCase serializa gravações, respeita cancelamento e só avança o controle de frequência após sucesso; chamadas finais forçadas continuam disponíveis. O ticker atualiza a interface antes de suspender para persistir; o salvamento final captura tipo/série antes do IO.

Essas mudanças preservam buffers e motores atuais. Compilação/lint/assinatura não medem estabilidade de uma conexão IPTV nem desempenho em aparelho. Nenhuma release instalada deve ser usada em testes de desenvolvimento.
