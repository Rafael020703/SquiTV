# Windows — início do aplicativo

## Escopo atual

**Publicada:** [Windows 1.22.2](https://github.com/Rafael020703/SquiTV/releases/tag/v1.22.2.1), exclusiva Windows. EXE online x64 de 70.990.336 bytes (67,70 MiB). Android permanece na v1.22 e continua Latest. A referência estável Windows aponta o manifesto da 1.22.2. A release instalada no PC não foi executada nem alterada pelo agente.

Windows x64: login Xtream, conta ativa/validade, dashboard, categorias/busca de canais, filmes e séries, detalhes de filmes, temporadas/episódios, reprodução integrada com LibVLC e configurações iniciais. Catálogos e preferências Windows ainda não têm paridade completa com o Android.

Ao abrir Ao vivo, a primeira categoria é selecionada; as demais são consultadas sob demanda. Não há consulta global automática na entrada da dashboard. A lista visível ao abrir o canal é congelada para anterior/próximo, incluindo o filtro de busca. A navegação é circular nessa origem, sem fallback global. Ao abrir a lista de canais durante a reprodução, o Windows exibe o painel lateral deslizante (`PlayerLayerPlacement.START`, 360 dp), alinhado com `LiveSplitView`/`ChannelListItem` do Android, exibindo cabeçalho estilizado, ícone/logo do canal e compartilhando `PlayerChannelRow`. As telas de catálogo (TV Ao Vivo, Filmes e Séries) utilizam a linha de categoria unificada `DesktopCategoryRow` com indicador lateral ativo, borda de foco e contagem de itens, além dos cartões de pôster (`CatalogPoster`) com gradiente e bordas de avaliação alinhados ao visual Android. Na tela de detalhes (`OnDemandDetailScreen`), a seção de fatos utiliza distintivos de metadados (`DesktopMetadataBadge`), a lista de temporadas usa `DesktopCategoryRow` e os episódios utilizam as tags numeradas (`PlayerEpisodeNumber`) e descrições (`PlayerEpisodeDescription`) compartilhadas com o Android. As Configurações Windows ainda têm paridade parcial. Opções sem consumidor real de idioma/segundo plano/janela foram retiradas da interface na revisão, com seus valores preservados. Qualidade ao vivo voltou à interface após receber consumidor real nas regras compartilhadas. Backup é somente de preferências Windows, local e separado por variante. Ordem e ocultação de categorias são isoladas por conta e por tipo (Ao vivo/Filmes/Séries); filmes/séries não reutilizam IDs ao vivo nem IDs globais legados. No player de séries, o menu de episódios também utiliza painel lateral deslizante (`PlayerLayerPlacement.START`, 380 dp) com `PlayerEpisodeRow` compartilhado. Ao encerrar o player, busca e posição da lista são preservadas na sessão. Favoritos, histórico/progresso persistente, categorias por gênero/preparação e sincronização ainda não foram portados. O atualizador Windows inicial está implementado, com os limites descritos adiante.

O núcleo `:shared` fornece modelos/parser Xtream, DNS, cores, índice circular, controlador de interação do player, montagem de endereço de canal e contrato de distribuição. Android e Windows usam as regras portáveis sem dependência entre aplicações. O desenho do launcher Android foi extraído para `buildSrc/BrandIcon`; dele saem também PNG/ICO Windows. Os ícones de ações usam a mesma família Material Rounded. Logos de canais vêm do servidor, com cache limitado a 64 imagens e até quatro consultas simultâneas.

## Dashboard — adaptação em andamento

A apresentação dos cartões e o fundo usam `CinematicCardContent`/`PortalBackdrop` extraídos do Android para uiCommon, com as mesmas regras numéricas de viewport 1280×720 em shared/ViewportSizing. Windows adiciona hover/mouse e percurso por setas, foco inicial em Ao vivo e restauração de foco/rolagem ao retornar dos catálogos. O estado da dashboard é removido na saída da conta. O relógio HH:mm atualiza no início de cada minuto.

Ao vivo, Filmes, Séries, Configurações e Buscar têm destinos reais. Favoritos, Downloads e Multi-view ainda exigem portabilidade funcional; os cartões dessas áreas continuam visuais. O botão de Notificações também não tem ação implementada no Android atual. Não confundir aparência compartilhada com paridade funcional concluída.

A rodada 1.22.2 foi encerrada por orientação do usuário; as demais portabilidades ficam para versões futuras, começando por revisar os fluxos existentes conforme feedback antes de ampliar funções da dashboard. A debug permanece separada das atualizações de produção. Por pedido expresso de 02/11/2026, desenvolvimento, execução e inspeções usam somente a **debug**; a release instalada no PC e seus dados não devem ser abertos, encerrados, reinstalados ou alterados pelo agente.

### Busca da dashboard

O acesso Buscar abre campo de texto e filtros Todos/Canais/Filmes/Séries, seguindo a organização de SearchScreen Android. Pesquisa títulos sem distinguir maiúsculas ou acentos; palavras separadas devem aparecer no título. Os resultados abrem o player ao vivo ou os detalhes existentes de filmes/séries, incluindo temporadas/episódios. Fechar o player/detalhes retorna à busca com texto, filtro, grade e alvo de foco salvos na sessão. Canais usam exclusivamente o snapshot dos canais visíveis nos resultados para a troca circular.

Como o Windows ainda não tem o catálogo Room Android, a primeira busca com texto consulta as listagens globais do provedor para os tipos selecionados, uma por vez. Não consulta detalhes individuais, episódios, EPG ou mídia para indexar, nem inicia consultas globais no login/dashboard. Listagens bem-sucedidas recebem índice em memória por até 10 minutos para reutilização em buscas seguintes; sair da conta descarta o índice. Digitação tem debounce de 250 ms; mudar consulta/filtro ou sair cancela o trabalho anterior, com revisão para impedir resultados antigos de substituírem os atuais. Filtragem/ordenação acontecem fora da UI. Retornar a uma busca concluída conserva o resultado; pesquisas interrompidas são retomadas.

Exibe até 300 resultados em ordem alfabética, com total e indicação para refinar a busca quando necessário. Falhas aparecem por catálogo, preservando resultados dos tipos que responderam e permitindo tentar novamente. O transporte mantém prazo de 45 s e limite de 16 MiB por listagem: provedores que excedam o limite apresentam erro, sem simular resultado vazio/completo. Busca de episódios individuais, voz e favoritos ainda não foram portados. Agrupamento de qualidades usa as regras Android compartilhadas. Esta etapa foi compilada na debug; não houve execução de busca/provedor ou inspeção visual pelo agente.

## Build

JDK 21 completo, com `bin/jpackage.exe`, e Windows x64. Definir `JAVA_HOME` para esse JDK na sessão. Não é necessário mudar o Java do Android Studio. O plugin Compose obtém suas ferramentas de empacotamento.

```powershell
# Release Windows online (padrão; requer compilador .NET Framework 4.x do Windows)
.\gradlew.bat :desktopApp:createDistributable :desktopApp:packageExe --console=plain

# Debug Windows, instalável ao lado da release
.\gradlew.bat :desktopApp:createDistributable :desktopApp:packageExe '-PdesktopVariant=debug' --console=plain

# Mesmo fluxo online, com identidade DEBUG para verificações isoladas
.\gradlew.bat :desktopApp:packageExe '-PdesktopVariant=debug' '-PdesktopInstaller=online' --console=plain
.\tools\verify-windows-installer.ps1
```

### Debug portátil para desenvolvimento

O fluxo preferencial da debug agora dispensa o instalador:

```powershell
.\gradlew.bat :desktopApp:createDistributable '-PdesktopVariant=debug' --console=plain
.\tools\deploy-windows-debug.ps1
```

O script copia a imagem autocontida para `%LOCALAPPDATA%/SquiTVDebugPortable/builds/<hash>` e atualiza o atalho **Squi TV Debug / SquiTVDebug** no menu Iniciar, usando o arquivo ICO da mesma marca Android, em caminho físico resolvido e endereçado pelo hash em SquiTVBrand/icons. Isso evita IconLocation apontando para um caminho lógico virtualizado diferente do alvo resolvido do executável. Não depender de ícones temporários do build nem apagar o cache de ícones do Windows. Cada build fica fora das pastas de saída Gradle; atualizar o atalho não encerra uma versão aberta. A cópia anterior do atalho é preservada. Builds idênticos reutilizam a cópia já concluída. O script não inicia o app e não apaga builds anteriores.

Cada variante admite uma instância por sessão Windows, inclusive ao abrir executáveis de builds portáteis diferentes. Um mutex nomeado é reservado antes de inicializar a interface/player; a segunda abertura sai e tenta trazer a janela existente para frente, sem restaurar/redimensionar sua tela cheia. Debug e release têm identidades distintas e podem coexistir. O Windows libera o bloqueio quando o processo termina, inclusive após uma falha; não há arquivo de trava para apagar. Builds antigos já abertos continuam intactos e não recebem esse bloqueio retroativamente.

Os dados continuam em `%LOCALAPPDATA%/SquiTVDebug`, inclusive DPAPI e preenchimento de desenvolvimento. `tools/configure-windows-debug.ps1` grava `debug-login.dpapi` como preenchimento local protegido, separado de `account.dpapi` (sessão opcional lembrada). Só o build debug habilita esse fallback; a release não o lê. A conta lembrada tem prioridade. Entrar sem lembrar pode remover a sessão, mas conserva o preenchimento da debug; esse fallback sozinho não ativa login automático. O script só cria uma sessão inicial se nenhuma estiver salva, sem sobrescrever outra conta. As credenciais fornecidas não são embutidas em fontes, JARs ou instaladores. O formulário mantém debugLoginDefaults separado da sessão também em memória: sair de uma sessão sem lembrar não deixa os campos vazios na debug. O deploy registra apenas o caminho físico do arquivo DPAPI externo em squitv.debug.defaultsFile no launcher local antes de calcular o hash; evita depender de visões diferentes de LOCALAPPDATA entre o host de desenvolvimento e o app. Essa propriedade é ignorada pela release. Não copie a conta para a pasta portátil nem para a release. O executável usa arquivos Java/VLC vizinhos: mova sempre a pasta inteira, não somente o `.exe`. Esta portabilidade diz respeito à aplicação; os dados continuam vinculados ao usuário Windows. A release mantém seu fluxo de instalador.

| Variante | Instalador | Diretório de build | Dados locais |
| --- | --- | --- | --- |
| Release | SquiTV-1.22.2.exe | desktopApp/build/release/compose/binaries/main/exe | %LOCALAPPDATA%/SquiTV |
| Debug | SquiTVDebug-1.22.2.exe | desktopApp/build/debug/compose/binaries/main/exe | %LOCALAPPDATA%/SquiTVDebug |

Cada variante tem nome no menu Iniciar e UUID de upgrade próprios. Preservar os UUIDs no Gradle em atualizações futuras. `packageExe` é o instalador da variante escolhida por `desktopVariant`; os nomes de tarefas de minificação do plugin não definem a identidade debug/release do aplicativo.

Ao alternar `desktopVariant`, tarefas com a mesma identidade Gradle podem limpar saídas antigas. Para conservar os dois instaladores, copie cada `.exe` concluído antes de compilar a outra variante. Os builds desta etapa foram preservados em `.artifacts/windows/release` e `.artifacts/windows/debug`, ignorados pelo Git. Isso não afeta a separação das aplicações já instaladas.

Java continua incluído. O instalador release online inclui o MSI do app e uma transformação pequena para upgrades legados por usuário; o VLC é verificado/reutilizado ou baixado e instalado automaticamente antes de executar o MSI. O usuário executa somente o `.exe`, sem scripts/Java/VLC manuais. Debug portátil/offline continua autocontida; a imagem release online sem instalador depende do cache preparado pelo setup. Os executáveis não têm assinatura Authenticode.

### Tamanho do instalador — 02/11/2026

O empacotamento Windows compila com a biblioteca Material Icons completa, mas inclui somente suas classes efetivamente referenciadas no bytecode desktop/uiCommon. `prepareDesktopIcons` descobre referências e dependências internas, copia os vetores originais sem alterar classes, conserva recursos/metadados e registra o SHA-256 da biblioteca de origem. Material Icons Core continua como dependência integral. Não há lista manual de ícones nem alteração do Android; novos usos nas fontes compiladas entram automaticamente no próximo build. Se forem adicionados módulos UI externos com uso de ícones extended, suas classes também precisam ser entradas da tarefa.

A mesma regra vale para debug e release e é dependência do classpath de empacotamento normal (`createDistributable`/`packageExe`). Não utiliza ofuscação/otimização global sobre Compose, serialização ou APIs JNA. Comparação local entre instaladores **debug** do mesmo código: 171.021.824 → 135.071.232 bytes, redução de **21,02%**. Biblioteca extended: 37.768.805 → 116.001 bytes; 30 classes mantidas com bytes idênticos aos originais. A imagem extraída caiu de 322.476.952 para 284.823.975 bytes. São medidas de artefatos; não comprovam execução visual/player nem uma nova release publicada.

`tools/audit_windows_package.py` faz inventário de todos os arquivos, grupos de runtime/VLC, JARs e hash/tamanho do EXE, sem executar o instalador. O JSON deve ficar em `.artifacts/`; o comando recusa substituir relatório existente:

```powershell
python tools/audit_windows_package.py desktopApp/build/debug/compose/binaries/main/app/SquiTVDebug --installer desktopApp/build/debug/compose/binaries/main/exe/SquiTVDebug-1.22.1.exe --output .artifacts/auditoria-instalador/build-novo.json
```

Essa foi a primeira redução, ainda com VLC embutido. O passo seguinte, implementado no Windows 1.22.1, separa seus 145.582.445 bytes extraídos para instalação/reutilização automática, mantendo o Java reduzido por jlink (79.799.469 bytes). O ZIP oficial tem 79.893.405 bytes; a primeira instalação sem componentes válidos ainda precisa desses bytes. Relatório histórico da primeira etapa em docs/relatorio-otimizacao-instalador-windows-2026-11-02.md; o fluxo atual está abaixo.

### Instalador online, atualização e desinstalação — Windows 1.22.1

- Contrato único `desktopApp/runtime/vlc.properties`: versão, x64, HTTPS oficial, tamanho e SHA-256. PrepareVlcRuntime verifica o arquivo e gera manifesto com hashes/tamanhos de cada arquivo selecionado, incluindo licenças/proveniência. O manifesto entra no app e no EXE; não depende de metadados modificáveis encontrados no cache.
- `tools/build-windows-online-installer.ps1` compila o bootstrapper C# com .NET Framework 4.8 do Windows, incorporando MSI, transformação de compatibilidade e seus tamanhos/hashes. `packageExe` release usa esse fluxo por padrão; não publica o MSI intermediário. O nome canônico e metadados de atualização EXE permanecem compatíveis com o updater 1.22.
- Setup verifica todos os arquivos do cache e rejeita DLLs extras, corrupção ou arquitetura errada. Um conjunto íntegro evita a rede; bibliotecas de uma instalação antiga da mesma variante também podem ser copiadas após verificação. Detecção usa o UpgradeCode preservado e o caminho registrado pelo MSI; versões antigas sem InstallLocation usam o keypath registrado do componente no MSI em cache. Não procura um VLC arbitrário em PATH.
- Quando necessário, baixa o ZIP oficial por HTTPS com validação de certificado, tamanho/SHA-256 exatos, prazo de 15 min e até três redirects somente VideoLAN. Download parcial não é promovido. Extração usa lista aprovada, limites, bloqueio de traversal/links e verificação completa antes de trocar diretórios. Cancelamento/repetição/reparo preservam o conjunto anterior. O app apenas verifica/carrega bibliotecas; não inicia downloads durante a reprodução.
- Primeira instalação: `Program Files\Squi TV`, para todos os usuários, com elevação UAC solicitada pelo Windows e escolha de pasta no MSI. O cache comum fica em `%PROGRAMDATA%\SquiTVComponents`. O bootstrapper elevado aceita somente a opção fixa `--machine`; não transporta credenciais ou comandos arbitrários. Conta/configurações continuam por usuário.
- Upgrade: preserva INSTALLDIR e contexto já registrados, sem abrir o seletor de pasta. As instalações antigas por usuário são mantidas nesse contexto usando MST autenticada; continuam com cache `%LOCALAPPDATA%\SquiTVComponents`, evitando uma migração destrutiva entre contextos Windows Installer. Esse caminho de compatibilidade não transforma silenciosamente instalações antigas em instalações de todos os usuários. Novas instalações seguem Program Files conforme pedido.
- O player procura apenas os caches conhecidos comum/por usuário da própria variante e verifica o manifesto antes de carregar DLLs. O modo debug portátil continua com VLC incluído; debug online usa `SquiTVDebugComponents`/UUID/dados próprios. Nunca executar a release instalada no PC em verificações.
- Remoção normal via Aplicativos do Windows: MSI remove arquivos/atalhos/registro de produto e chama Maintenance apenas com `REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE`. Limpa configurações/DPAPI e caches de componentes/MSI/ZIP da variante. Remoção por máquina limpa dados próprios nos perfis locais registrados; versão debug é preservada. Links/junctions são removidos sem seguir seu destino. Falha de limpeza retorna erro ao MSI, sem fingir remoção completa.
- A limpeza é excluída do major upgrade; dados/contas/favoritos e o runtime reutilizado permanecem durante atualizações. Não apaga arquivos pessoais fora das pastas próprias, o EXE que o usuário baixou, a árvore do projeto ou caches/registros de instalação administrados pelo Windows. Windows Installer e o sistema gerenciam seus próprios logs/prefetch/cache protegido.

Verificações automatizadas usam somente recursos debug e diretórios isolados: preparação inicial, reutilização offline, corrupção de mesmo tamanho, reparo, migração de fixture sem alterá-la, arquivo extra, cache MSI/MST, cancelamento, traversal, arquitetura, ZIP truncado, pastas/condições reais do MSI/MST e limpeza de fixtures preservando debug/outro app. Download HTTPS real com SHA-256 foi executado. Isso não equivale a executar o MSI com UAC, instalar/desinstalar ou testar o player em uma instalação real; a release instalada permanece intacta.

### Runtime de vídeo

`prepareVlcRuntime` obtém o ZIP oficial VLC 3.0.23 Windows x64, verifica tamanho/SHA-256 da definição versionada e prepara DLLs/plugins/licenças e manifesto. O arquivo de build fica em `.artifacts/native`, fora do Git; a extração fica em `build/desktopNativeResources`. Debug offline embute a seleção; release online leva somente o contrato e prepara componentes pelo setup. Não remover avisos/licenças.

O adaptador `WindowsLivePlayer` usa JNA diretamente com a API C LibVLC; não depende de player Android ou de executável VLC externo. Comandos nativos ficam em um único worker; a interface não executa chamadas de vídeo bloqueantes. A janela de vídeo usa Canvas nativo e os controles sob demanda ficam abaixo dela. O player ao vivo usa HUDs Compose em janelas JWindow pertencentes à janela principal, posicionadas acima do Canvas: não copia frames nem depende de blending experimental. Os HUDs acompanham posição/tamanho, escondem ao trocar de aplicativo e são descartados ao sair do player. Seus ComposePanel usam somente `RenderSettings.SwingGraphics()` (API experimental já presente na versão instalada): o SkiaSurface padrão desses painéis aplicava fullscreen=false ao ser mostrado e limpava a tela cheia da janela principal no monitor. O renderer da janela principal e a superfície nativa LibVLC não foram alterados; não há loop para forçar/restaurar fullscreen. Não foram medidos impactos de CPU/desempenho.

Para desenvolvimento: `.\gradlew.bat :desktopApp:run '-PdesktopVariant=debug'`. O Gradle prepara o runtime e fornece seu caminho apenas à execução local, sem embutir o caminho do checkout no instalador.

| Atalho durante reprodução | Ação |
| --- | --- |
| Espaço | Pausar/retomar |
| Page Up / Page Down | Canal anterior/próximo na lista de origem |
| F | Alternar tela cheia |
| Esc | Fechar menu/controles/aviso e depois encerrar a reprodução; mantém tela cheia |
| F11 | Alternar tela cheia em qualquer tela |
| ↑ / ↓ com controles ocultos | Canal anterior/próximo na lista de origem |
| Enter/Espaço com controles ocultos | Mostrar controles |

O EPG é consultado para o canal atual e atualizado a cada minuto enquanto a tela de reprodução permanece aberta. Mudança de canal ou saída cancela a consulta anterior. Falhas de EPG não impedem o vídeo. Não existe tela EPG separada.

### Janela limpa

A janela não exibe moldura, título nativo, nome/debug/versão internos ou faixa reservada para controles. A faixa de 28 dp e os botões por hover foram removidos após a correção solicitada pelo usuário; não os recolocar. Também foram removidas as margens globais vazias. O perfil na dashboard, o cabeçalho das listas e a área institucional do login permitem arrastar a janela sem uma barra adicional. Não há botões visuais de voltar/Início; Sair da conta permanece somente nas Configurações.

Fora da tela cheia, tamanho inicial de 1120×630, proporção 16:9 mantida nos eventos de redimensionamento e mínimo 960×540 (reduzido proporcionalmente em monitores menores). Maximização comum retorna ao modo janela para preservar a proporção. F11 alterna tela cheia em todas as telas; o modo ocupa os limites do monitor, bloqueia redimensionamento/arraste e usa a proporção nativa do monitor. Somente F11 ou a ação de tela cheia retornam ao modo janela; Esc navega sem alterar esse modo. A navegação entre telas não encerra a tela cheia. O modo janela/tela cheia é persistido por variante no preferences.json. Na primeira abertura (inclusive preferências antigas sem esse campo), o padrão é tela cheia. A janela só se torna visível após restaurar a preferência, evitando exibir primeiro o modo errado. Na raiz (dashboard ou login), Esc/Voltar mostra “Deseja realmente fechar o Squi TV?” com FICAR e SAIR, reutilizando o componente Compose extraído da confirmação Android. FICAR recebe foco inicial; Esc ou clique fora cancela. A confirmação Windows é sobreposta na própria superfície, sem criar janela auxiliar que altere fullscreen. Nas demais telas, Voltar navega primeiro; no player ao vivo, fecha os menus/controles/aviso antes de retornar à lista. A ação é tratada ao soltar a tecla para impedir repetição atravessando telas. O fechamento confirmado, assim como Alt+F4, aguarda a gravação serializada da preferência. O título/ícone continuam identificando o app no sistema, sem faixa visível ou mudança de versão.

## Filmes, séries e configurações — primeira etapa funcional

- Modelos e serializers `XtreamVod`, `XtreamSeries`, `XtreamSeason`, `XtreamEpisode`, detalhes e serializers tolerantes foram movidos do Android para `shared/.../data/model/XtreamOnDemandModels.kt`, mantendo pacote, campos e nomes públicos. As duas aplicações compilam a mesma implementação. Parcelable de categorias/canais permanece Android.
- As categorias/listas são consultadas somente ao abrir cada catálogo/categoria. Sem preparação automática ou novas consultas globais na dashboard. Mudança de categoria/catálogo, saída e logout cancelam os trabalhos anteriores; dados em memória são descartados na troca de conta.
- Interface baseada em MoviesScreen/SeriesScreen/PosterCard: cabeçalho, sidebar, pôsteres, nota quando informada, busca local e alternância de ordenação A–Z/origem. Ao fechar detalhes, a grade usa seu estado de navegação salvo na sessão.
- Detalhes exibem capa, sinopse, gênero, data, elenco/direção fornecidos pelo servidor. Séries exibem temporadas em sidebar independente e episódios em lista lazy, seguindo a organização de SeriesDetailScreen Android. A temporada 1 é a escolha inicial quando existe; especiais (temporada 0) ficam por último. Seleção da temporada e rolagem dos detalhes são preservadas ao sair para reproduzir e voltar na mesma sessão; o estado anterior é descartado ao fechar/trocar os detalhes. Miniaturas, sinopses e duração aparecem somente quando fornecidas. O parser/modelo Xtream Android é compartilhado; OnDemandPlayback resolve as mesmas chaves tanto na descoberta quanto na seleção dos episódios (números com zeros, Season/Temporada e nomes declarados pelo servidor), respeitando o campo season explícito. Não infere temporada de números arbitrários em nomes. Consultas continuam sob demanda ao abrir detalhes. Erros têm tentativa explícita, sem registrar URLs/credenciais.
- Filmes/episódios usam a mesma superfície e adaptador LibVLC do ao vivo, com modo de reprodução sob demanda separado. Há play/pausa central, linha de tempo quando duração/seek são suportados, saltos de 10 s, volume/silêncio, tela cheia e episódio anterior/próximo. Reprodução automática segue a sequência de temporadas/episódios da série, sem loop. Não há EPG fora do player ao vivo. Progresso entre sessões ainda não é gravado.
- Configurações iniciais: Atualizações, Conta, Auto-reprodução de Episódios, Sair da conta, Testador de DNS e Zoom da Interface, ordenadas alfabeticamente e com ícones/grade do Android. Somente as opções já implementadas são apresentadas. DNS reaproveita a sondagem existente, sem trocar servidor da sessão ativa. Volume/silêncio do player sob demanda, zoom e reprodução automática ficam no `preferences.json` da variante, sem credenciais; escrita serializada/atômica e erro de leitura preservando o arquivo. As demais opções Android permanecem no roteiro de portabilidade.
- Compilar a debug e atualizar o atalho ao fim de cada etapa concluída, conforme pedido do usuário de acompanhar builds mais frequentes. Hot Reload foi apenas investigado: nenhuma configuração, servidor ou processo de atualização a quente foi ativado. Um processo aberto continua usando seu build até a próxima abertura; não encerrá-lo automaticamente.

## Player ao vivo — adaptação Android em andamento

O controlador de visibilidade/interação `PlayerOverlayController` foi movido intacto do Android para `:shared`; as duas plataformas usam os mesmos 5 segundos, renovação na interação e suspensão durante interação contínua/menu. `CenteredPlayerActions` e `CompactChannelBanner`, extraídos da geometria original Android para uiCommon, são compilados pelas duas aplicações; imagens e foco permanecem adaptados por plataforma.

No Windows, o vídeo ocupa o player inteiro. Os controles aparecem com mouse, clique ou Enter/Espaço e ocultam por inatividade. Pausa fica centralizada, há anterior/próximo e uma lista que usa exclusivamente o snapshot da categoria/busca de origem. Esc fecha primeiro menu/controles/aviso, depois retorna à lista, sem sair da tela cheia. O aviso compacto na troca dura 3 segundos; EPG/progresso e próximo programa usam os dados existentes, somente durante reprodução. Erros permanecem legíveis; volume/silêncio agora compartilham as preferências persistentes também no ao vivo.

O menu segue a organização Android (abas adaptadas por largura): Imagem, Áudio, Legendas, Velocidade e Temporizador. Proporção original/16:9/4:3, faixas anunciadas pelo LibVLC, legendas desativáveis, velocidades suportadas e temporizador têm comandos nativos/ação efetiva; falhas de escolha são apresentadas sem encerrar o vídeo. A seleção de faixas depende da transmissão e pode ser atualizada no menu. Agrupamento/seletor de qualidade ao vivo estão implementados; favoritos, PiP e outros detalhes Android ainda não têm paridade completa.

## Conta e rede

- “Lembrar minha conta” é opcional. DPAPI vincula o arquivo `account.dpapi` ao usuário Windows; não gravar credenciais em JSON aberto, logs ou argumentos de processo.
- A conta lembrada por DPAPI é reconectada uma vez na abertura, sem pedir novo clique no login; falhas retornam ao formulário preenchido, sem loop de tentativas automáticas. Cancelar interrompe essa tentativa. Sair da conta grava login.paused na pasta da variante, impedindo reconexão automática até a próxima entrada manual bem-sucedida. Credenciais de desenvolvimento continuam fora do código e das distribuições.
- Consultas Xtream tratam redirecionamentos 301/302/303/307/308 explicitamente: no máximo três saltos e quatro requisições, ciclos recusados e prazo total de 45 segundos. Aceitam somente o mesmo hostname selecionado, sem downgrade HTTPS→HTTP, user-info na URL ou troca de username/password nos parâmetros. A porta pode ser alterada pelo mesmo servidor; credenciais não são copiadas automaticamente a um destino sem elas. Respostas/corpos são fechados antes do salto e o cancelamento cancela a chamada ativa. Isso corrige a recusa de 301 observada em get_series_info; redirects globais do cliente continuam desligados. Falha dos detalhes fica visível no topo, junto de Tentar novamente, em vez de abaixo da sinopse. Capa/descrição da listagem não são evidência de temporadas consultadas.
- A lista de DNS do provedor é a mesma do Android, em `shared/xtream/ProviderServers.kt`. Não altera os resolvers DNS do Windows. A sondagem HEAD não transmite credenciais e mede disponibilidade/latência, sem comprovar autenticação.
- Sem endereço personalizado, o login prioriza o DNS escolhido ou o mais rápido e tenta alternativas em falhas de conexão/resposta. Recusa de autenticação, bloqueio ou expiração encerram as tentativas. Cancelar interrompe a chamada; cada tentativa de login tem prazo de 15 segundos.
- Endereço personalizado é exclusivo: não encaminhar suas credenciais aos demais provedores.
- “Sair da conta” encerra a sessão; a conta lembrada continua disponível. Entrar desmarcando “Lembrar” remove a cópia salva da variante atual e não ativa reconexão automática.
- Requisições respeitam cancelamento e limites de tempo/tamanho. Trocar categoria cancela a consulta anterior. Redirects não encaminham credenciais a outro endereço; usar o endereço final do provedor.
- HTTP é aceito para provedores que o exigem; o cliente de atualização Android mantém política HTTPS independente.

## Credenciais de desenvolvimento

Não embutir contas reais em recursos, constantes, APKs/EXEs ou arquivos rastreados. A debug pode receber as mesmas credenciais de teste do Android pelo script local:

```powershell
.\tools\configure-windows-debug.ps1 -CredentialsFile desktopApp/debug-login.local.json
```

O JSON privado usa `baseUrl`, `username` e `password`. O caminho indicado acima é ignorado pelo Git. O script grava somente o arquivo DPAPI de SquiTVDebug; a release não recebe esse preenchimento. Não inventar dados de teste nem substituir uma conta existente por credenciais de fixtures.

## Paridade com o Android

A referência é o código Android existente. A versão Windows inicial não é uma cópia completa. Cada função abaixo precisa de implementação real e equivalência visual antes de declarar paridade funcional. Por solicitação expressa do usuário, os botões da dashboard ainda sem backend Windows já são apresentados com seu visual Android; isso não significa que suas funções foram portadas.

| Área Android | Estado Windows |
| --- | --- |
| Login, conta ativa, validade e servidor | Implementados; não há ainda gerenciador de múltiplas contas/perfis |
| DNS automático/manual e alternativas | Implementados no login; lista compartilhada com Android |
| Identidade visual do login | Cores e logo compartilhados; composição institucional/formulário baseada no Android |
| Preenchimento da debug | Suporte local protegido; depende das credenciais reais fornecidas para desenvolvimento |
| Dashboard, relógio, atalhos e organização | Seis cartões Android e atalhos Busca/Notificações/Configurações presentes visualmente; TV ao vivo, Filmes, Séries e Configurações possuem navegação implementada. Saudação, validade e relógio usam dados reais |
| Categorias e listagem ao vivo | Grade de cartões quadrados com logos, cabeçalho e sidebar baseados no Android; agrupamento SD/HD/FHD/4K e preferência de qualidade implementados com regras Android compartilhadas; favoritos pendentes; ordem e ocultação ao vivo por conta implementadas |
| Player, EPG dentro da reprodução, áudio, legendas, proporção e temporizador | Ao vivo com HUD, EPG, controles, menu nativo de faixas/proporção/velocidade/temporizador implementados; comportamento final ainda sem validação visual. Paridade completa pendente |
| Troca circular restrita à categoria/lista de origem | Implementada com índice compartilhado e snapshot da lista visível; favoritos ainda não disponíveis no Windows |
| Filmes, detalhes, atores, categorias servidor/gênero e preparação | Catálogo do servidor, busca, detalhes textuais e reprodução iniciais implementados; navegação por ator/gênero, preparação e biblioteca persistente pendentes |
| Séries, temporadas, episódios e continuação | Catálogo/detalhes, temporadas, episódios, reprodução e sequência automática iniciais implementados; histórico/retomada persistente pendentes |
| Favoritos, histórico, progresso e perfis por conta | Pendentes; preservar escopos e formatos Android |
| Busca global | Busca por títulos de canais/filmes/séries do provedor, filtros, detalhes/reprodução e retorno à grade implementados; agrupamento ao vivo compartilhado implementado; episódios individuais e voz pendentes; sem validação em execução nesta etapa |
| Downloads e multiview | Pendentes |
| Configurações, categorias ocultas/ordem personalizada e zoom | Conta, DNS, zoom, auto-reprodução, agrupamento/qualidade ao vivo, personalização da dashboard e organização/ocultação de categorias por conta/tipo; demais opções pendentes |
| Importação/exportação seletiva e sincronização local | Pendentes; manter contrato compatível com Android |
| Atualizações Windows | Tela nas Configurações em ambas as variantes; rede/download/instalação habilitados somente na release. Fluxo compilado/empacotado, sem atualização real exercitada |
| Login pela rede local | Pendente |
| PiP e reprodução em segundo plano | Pendentes de adaptação nativa; manter funções distintas |

Orientação atual do usuário (02/11/2026): foco no Windows, reaproveitando **visual e funções** Android. Player VOD, atualizador inicial e busca da dashboard estão implementados; a paridade funcional completa continua pendente. Manter a continuidade incremental, uma parte por execução agendada, usando somente debug e preservando a release instalada. Os demais botões ainda sem função da dashboard continuam visuais por autorização anterior; sua presença não comprova portabilidade. Não declarar concluída a paridade completa enquanto as pendências da tabela existirem.

A arte vetorial dos seis cartões foi extraída sem redesenho de `CinematicActionCard.kt` Android para `uiCommon/src/main/kotlin/.../HomeCardGraphic.kt`; as duas plataformas compilam a mesma fonte. O Windows conserva as duas fileiras de três cartões, ícones Material Rounded, gradiente, cores e indicadores de foco do Android, com adaptação da composição a cartões/janelas estreitos. Não consulta filmes/séries/downloads/favoritos para montar esses botões e não inventa contadores de conteúdo.

Dashboard, categorias, cartões e controles usam referências Android; raio de cartão, proporção quadrada, largura de sidebar e borda de foco estão em `shared/brand/AppBrand`, consumidos nas duas plataformas. A sidebar se adapta a janelas menores e o Windows destaca mouse/teclado.

A escala original de `Type.kt` foi extraída para `shared/brand/BrandTypography`: tamanhos, pesos, espaçamento e altura de linha são consumidos por Android e Windows com adaptadores Compose próprios. Os valores Android e a tipografia legada foram preservados. Formulário de login usa os ícones Person/Lock/Visibility, campos escuros com foco ciano e avisos consistentes. As barras de rolagem aparecem apenas quando há conteúdo excedente na sidebar, na grade ou no formulário; não representam novas configurações.

O cabeçalho Ao vivo adapta a busca à largura disponível: abaixo de 920 dp úteis o campo ocupa uma linha própria, preservando espaço para o nome da categoria. O subtítulo aceita até duas linhas. Em janelas largas, título e busca continuam lado a lado; o filtro e a lista de origem da reprodução permanecem os mesmos.

Os estados de carregamento, vazio e erro da listagem ocupam o espaço disponível e centralizam a mensagem e a ação existente de tentar novamente, seguindo AppStates Android. Em pouca altura, ícone/espaçamento diminuem e o conteúdo admite rolagem; não há altura mínima fixa que dispute espaço com o botão. Isso não altera o tratamento de erros nem a consulta de canais.

## Verificação e limites

Há testes existentes de parser/conta, transporte HTTP, cancelamento, DPAPI e renderização. A existência desses testes não comprova a reprodução nativa. Não executar ou ampliar testes automaticamente sem pedido expresso. Os previews existentes usam dados fictícios e ficam em `desktopApp/build/preview`.

O argumento técnico `--self-check <arquivo-de-saída>` verifica o runtime empacotado, o contrato compartilhado e um ciclo DPAPI em diretório temporário. Não acessa a conta real nem se conecta ao provedor.

Em 02/11/2026 o usuário confirmou que os canais reproduziram com sucesso no Windows. Essa é evidência relatada pelo usuário, não execução instrumentada pelo agente nem validação de todos os controles/provedores. O agente observou o login de um build intermediário em tela cheia sem a faixa superior/laterais vazias; não interagiu com a conexão em andamento. A janela 16:9/F11 e as novas telas/controles de filmes/séries/configurações foram compilados, mas não foram exercitados visualmente nem com o provedor nesta etapa. Nenhum teste foi executado nesta rodada. Não declarar paridade completa ou compatibilidade universal. Build não atualiza a instalação anterior; a debug portátil passa a ser acessada pelo atalho apontado pelo script.

## Próxima distribuição

A release Android v1.21 não contém instalador Windows. Não alterar seu manifesto após a publicação. Uma futura release conjunta deverá declarar os artefatos Windows realmente compilados no mesmo contrato schema v1, com nome, tamanho e SHA-256 exatos, preservando o APK exigido pelos clientes Android legados.

## Visual compartilhado dos players — 02/11/2026

A solicitação atual é aproximar todos os players Windows do Android, com reaproveitamento efetivo. `uiCommon/.../player/PlayerChrome.kt` concentra título, botão de pausa (72/90 dp), ações, linha do tempo/indicador de seek, botões circulares, selo ao vivo, progresso EPG e cartão LiveBottomOverlay. O Android passou a chamar esses mesmos composables, preservando adaptadores Media3, foco e callbacks; o Windows os usa sobre o LibVLC. `PlayerSettingsPresentation.kt` contém o mesmo painel adaptativo, abas, foco inicial, cores/seleção e linhas de opções nas duas plataformas.

Filmes e séries Windows têm título no topo, gradientes e controles inferiores com pausa central, seek e configurações. O ao vivo usa o cartão Android com logo, nome/programação, EPG e botões circulares; conserva as ações Windows já existentes, incluindo volume, anterior/próximo e pausa. Listas de canais/episódios continuam disponíveis e usam superfície escura. Não foram criados botões de favoritos/PiP sem implementação. Diferenças funcionais entre plataformas continuam documentadas.

O HUD continua em JWindow/ComposePanel com SwingGraphics, sem mexer no Canvas LibVLC ou no modo de tela cheia. Solicita transparência por pixel somente quando suportada pela configuração de vídeo, com fundo escuro de compatibilidade; a transparência foi implementada, mas ainda não observada em execução pelo agente. Composables dos controles são removidos quando o HUD é ocultado para não manter o selo pulsante ativo durante a reprodução sem controles. F11/Esc, snapshot da origem, seek e temporizadores permanecem nos adaptadores existentes.

Compilação de fontes Android debug/release e debug portátil Windows concluída; `git diff --check` sem erros. Build debug `4a0b0f18d92ddd47` disponibilizado pelo atalho do Iniciar, sem iniciar/encerrar processos. Nenhum teste acrescentado/executado, execução visual ou reprodução observada nesta etapa. A release instalada e os assets publicados não foram alterados; não houve mudança de versão, push ou publicação.

## Player de filmes/séries e Atualizações — 02/11/2026

O VOD reaproveita PlayerOverlayController, PlayerSeekController e CenteredPlayerActions do Android. Controles ocultam após 5 segundos de inatividade, mantêm pausa central e suspendem ocultação durante seek/menu/interação contínua. Seek por teclado é coalescido em 300 ms, cancelável por Esc/Voltar; duração e seekability vêm do player. A confirmação do alvo considera a direção para não aceitar posição antiga em saltos para trás. Filmes têm três controles centrais; séries têm cinco incluindo episódio anterior/próximo, além de lista de episódios do snapshot atual. Não há loop entre último e primeiro episódio. sourceId/generation evitam usar ended de uma reprodução anterior. Temporada do episódio reproduzido é selecionada ao retornar. Áudio, legendas, imagem, velocidade e temporizador usam o menu e comandos LibVLC existentes. Histórico/retomada entre sessões permanecem pendentes.

Configurações → Atualizações fica visível nas duas variantes. Debug exibe a versão instalada e estado de desenvolvimento, sem consultar, baixar ou instalar produção. Release oferece verificação, novidades, download com progresso/cancelamento, repetição após falha e instalação explicitamente confirmada pelo botão “Instalar e fechar o app”. A abertura do instalador fecha o app apenas depois do lançamento aceito pelo Windows. Não existe instalação automática pela rotina.

A tela usa os mesmos painéis de status/versões Android extraídos para uiCommon, largura máxima de 720 dp, etapas Baixar/Verificar/Instalar, ações legíveis e novidades recolhíveis. O filtro de notas releaseHighlights mantém a implementação compartilhada, sem expor seções técnicas de download/checksum. O aviso da dashboard reutiliza UpdateNoticeContent, com 10 segundos em primeiro plano e opção de ignorar uma versão.

WindowsUpdateRepository usa cliente público GitHub separado do IPTV, cancelamento de chamadas/corpos, limites de tempo/tamanho, HTTPS e redirects limitados a hosts GitHub/CDN autorizados. Confere manifesto schema v1, associação exata tag/asset/URL/tamanho e concordância SHA-256 com digest/sidecar disponíveis. Seleciona apenas EXE canônico release de versão superior e arquitetura compatível. Verifica tamanho, cabeçalho MZ e SHA-256 antes de disponibilizar/abrir o instalador. O hash comprova integridade com o manifesto, não assinatura Authenticode do editor; instaladores atuais não possuem essa assinatura. Cache de descoberta de 12 h somente na sessão para resultados bem-sucedidos; erros permitem nova tentativa. A preferência ignorada é isolada por variante.

Sair da conta aparece exclusivamente nas Configurações. Removidos ícones Home/Início dos cabeçalhos/listagem; Esc/Voltar mantém o fluxo de retorno e, na tela de Atualizações, retorna às Configurações. F11 continua controlando tela cheia, independentemente de Esc. Não foram acrescentados botões visuais de voltar.

Verificação desta etapa: fontes compartilhadas compiladas para Android debug/release; Windows debug portátil gerada e atalho do Iniciar atualizado; EXE release empacotado localmente. Nenhum teste acrescentado/executado, nenhuma janela aberta/encerrada pelo agente, nenhum novo controle/player/fluxo de atualização observado em execução. Canais ao vivo funcionando é relato anterior do usuário. Não houve push, publicação, instalação ou mudança de versão. tools/generate_manifest.py permanece dedicado ao Android; prepare_release.py agora prepara o manifesto conjunto, as notas por plataforma e os candidatos de referência estável. writeUpdateMetadata vincula o EXE real a versão/arquitetura/hash/tamanho. Publicação e avanço das referências continuam pendentes de autorização.

### Distribuição por plataforma

Release conjunta [v1.22](https://github.com/Rafael020703/SquiTV/releases/tag/v1.22): Android 1.22/code 23 e Windows 1.22.0. Assets públicos e referências das duas plataformas conferidos na publicação. A CI do commit de release aprovou Android e Windows release/debug; não houve instalação/upgrade nem reprodução nativa pelo agente. Correções isoladas podem usar o mesmo número em cada app, por exemplo Android 1.22.1 e Windows 1.22.1; tags Android v1.22.1 e Windows v1.22.1.1 evitam colisão sem invalidar Android antigos. O título Windows mantém v1.22.1, sem a componente técnica da tag.

Changelog novo vem da chave windows do manifesto; Android lê android. Fallback de body preservado somente em manifestos antigos de plataforma única. Ao esgotar histórico ou limite de manifestos, a referência fixa Windows resolve sua própria release por tag/hash, independentemente de quantas Android foram publicadas. Referências candidatas são geradas localmente e só entram em updates/stable depois de existir a release real.

### Verificação da publicação Windows 1.22.1

CI do código e280c20 aprovada ([execução](https://github.com/Rafael020703/SquiTV/actions/runs/41523154187)). Os três assets públicos foram baixados novamente e comparados por tamanho/SHA-256 com o staging final. Um verificador temporário usando classes DEBUG executou o WindowsUpdateRepository real contra o GitHub e descobriu 1.22.1 a partir de 1.22.0, conferindo tag/hash/notas Windows. Não abriu app ou instalador, nem removeu os bloqueios da UI debug. A primeira consulta imediatamente após publicar não encontrou a atualização; a repetição encontrou a publicação. Isso não equivale a executar o upgrade da release instalada ou observar a interface/UAC.

## Revisão das alterações Gemini — 02/11/2026

Snapshot de 25 fontes/documentos preservado localmente em `.artifacts/revisao-gemini-2026-11-02/before`. Mantidos os componentes visuais comuns, logos/listas laterais, metadados, personalização de seções e menu ao vivo compacto. A revisão não identifica autoria apenas pelo diff: havia mudanças Codex e Gemini misturadas sem commits intermediários.

| Problema confirmado no código | Correção |
| --- | --- |
| Ao vivo solicitava foco no botão de pausa removido | Foco ligado ao acesso real à lista; pausa por Espaço e tentativa explícita em erro |
| Número do episódio preto sobre fundo preto no foco | Contraste do marcador e sinopse corrigidos; pausa VOD com espaço de 72 dp |
| Dashboard criava foco fictício ao ocultar todos os cartões; categorias observavam source de foco sem ligação | Fallback para Buscar e interação própria da linha |
| Idioma/qualidade/segundo plano/janela só gravavam preferências sem efeito | Opções removidas da interface; campos existentes preservados para compatibilidade |
| Redefinir base reconectava e salvava credenciais com remember=true | Ação redundante removida; limpeza limita-se ao cache de catálogos/busca |
| IDs globais de categorias ao vivo escondiam filmes/séries e outras contas | Novo escopo de conta apenas ao vivo; seleção visível reconciliada; legado ambíguo preservado sem aplicação/migração automática |
| Backup sempre usava SquiTVDebug, fazia I/O na UI e anunciava sucesso antes da gravação | Pasta por variante, I/O fora da UI, limite de 64 KiB, schema/faixas validados, arquivo atômico e confirmação após escrita |
| Atualização de categorias podia continuar após logout e mostrar sucesso na falha | Job/revisão canceláveis, erro visível e descrição restrita às categorias ao vivo |
| Extração do cartão de Configurações removeu medidas responsivas Android | Tokens/tamanhos/fontes/borda/forma Android restaurados por parâmetros compartilhados |

Valores antigos sem consumidor e IDs de ocultação sem conta/tipo não são apagados nem atribuídos por suposição. Backup não inclui credenciais, favoritos, catálogo ou compatibilidade com o backup Android. Sua localização é `%LOCALAPPDATA%/<variante>/squitv-backup.json`. A limpeza de cache não apaga imagens, dados pessoais nem uma base persistente inexistente.

Verificações desta revisão: leitura do diff/fontes, `git diff --check`, compilação Windows debug portátil e fontes Android debug/release, com sucesso. Debug `fa2971e796f5fe4b` no atalho do Iniciar, sem iniciar/encerrar processos. Nenhum teste adicionado/executado nem interface/player observado em uso. Nenhum push/tag/publicação/mudança de versão ou edição na release instalada.

### Persistência: limite de gravação — 02/11/2026

O `DesktopPreferencesStore` já recusava arquivos maiores que 64 KiB na leitura, mas a gravação normal não conferia o tamanho serializado. Um conjunto grande de preferências podia ser salvo e recusado pelo próprio app na abertura seguinte. Agora a escrita mede os bytes UTF-8 antes de criar o temporário/substituir o arquivo; se ultrapassar o mesmo limite da leitura, mantém arquivo/estado anterior e informa falha pelo tratamento existente. Sem truncamento, limpeza ou migração de dados.

Verificação: leitura do caminho de gravação/erro, `git diff --check` e build Windows debug portátil concluído (4 s). Build `a43b7afe458e2c2f` disponível no atalho do Iniciar. Nenhum teste, leitura/escrita das preferências reais, execução de app ou mudança na release instalada nesta rodada.

## Paridade ao vivo: identificação de qualidade — 02/11/2026

Os cartões ao vivo Windows agora mostram SD/HD/FHD/4K quando a identificação está presente no nome informado pelo provedor. `ChannelQuality` e o parser original Android foram extraídos para `shared/.../domain/model`; `LiveChannelGrouping.parse` usa a mesma implementação que o Windows. Mantidos números, regiões, idiomas e nomes legítimos, marcadores completos e tratamento conservador de rótulos conflitantes. A qualidade é a descrição do provedor, não medição da resolução reproduzida; sem identificação reconhecida, nenhum selo aparece.

`uiCommon/.../cards/ChannelQualityBadge.kt` concentra o selo original Android, usado pelas duas plataformas com seus tokens de tema. O Android continua considerando a maior qualidade das versões agrupadas. Esta etapa não agrupa canais Windows, não escolhe outra URL e não altera snapshot/navegação/reprodução. A próxima fatia é compartilhar a regra de formação dos grupos, preservando conta, categoria, EPG e todas as versões.

Build conjunto Windows debug portátil e fontes Android debug/release concluído (10 s; após limpeza dos imports, 4 s). `git diff --check` sem erros. Debug `fb7123484ac716bb` entregue pelo atalho do Iniciar, sem iniciar/encerrar processos. Sem testes adicionados/executados ou inspeção visual real nesta etapa. Release instalada, versões e distribuição preservadas.

## Paridade ao vivo: agrupamento, versões e busca — 02/11/2026

A formação das famílias e a escolha de qualidade originais Android agora estão em `shared/.../domain/model/LiveChannelFamilies.kt`; o Android mantém seu adaptador IptvItem/ChannelVersion e favoritos. O Windows usa `LiveChannelGroups` sobre os dados Xtream já carregados. Conta, categoria, nome normalizado e identidade EPG delimitam as famílias; versões desconhecidas/alternativas permanecem disponíveis. Não agrega idiomas, regiões ou números diferentes por heurística nova.

Ao vivo e busca global apresentam os grupos quando a preferência existente está habilitada (padrão). A grade usa chaves de grupo estáveis, selo da maior qualidade identificada e ordem alfabética, seguindo CategoryViewModel Android. A busca também usa os cartões ao vivo já existentes, sem apresentação duplicada; contagens consideram grupos e o limite de resultados é aplicado após agrupamento. Preparação/filtro/ordenação da grade ocorrem fora da thread de interface; resultados cancelados não substituem a projeção atual.

Configurações → Canais ao vivo oferece agrupar e qualidade preferida; os campos antes sem consumidor agora têm efeito e só então voltaram à UI. No player, a aba Canais oferece agrupamento e seleção de um endereço real do provedor, usando o mesmo painel/opções Android. VOD continua sem essa aba. Alterar agrupamento preserva o stream tocando; o player mantém todas as versões do snapshot da lista aberta. Anterior/próximo pulam famílias e fazem loop apenas nesse snapshot, sem consultar lista global ou reiniciar uma lista com um único canal. Preferência escolhe a versão para a próxima abertura/troca, sem interromper a atual.

Build conjunto Windows debug e fontes Android debug/release concluído; build final Windows 5 s. Debug `6196e510b0bc0d58` no Iniciar (intermediário `c6ff0c4d6f56c021` preservado). Diferença revisada e sem erros de whitespace após correção de newline. Sem testes adicionados/executados nem observação real da nova UI/player. Nenhum processo reiniciado, versão alterada, release instalada modificada ou publicação feita.

## Filmes/Séries: retorno à grade e consultas repetidas — 02/11/2026

O ContentGridState original Android foi movido sem alterar seu Saver para uiCommon, mantendo pacote/API, e passou a ser usado pelos catálogos Windows. Salva somente índice/offset/chave de foco e interação; nenhum catálogo ou credencial entra no estado salvo. Windows separa o estado por conta (hash), tipo, categoria, ordenação e busca. Ao retornar dos detalhes em navegação por teclado, recupera o cartão e desloca a grade apenas se ele estiver fora da área visível. A busca com foco não é interrompida por um pedido automático de foco da grade.

O controlador distingue uma listagem vazia carregada de uma ainda não carregada, seguindo o reaproveitamento de requisição concluída do CategoryViewModel Android. Voltar para uma categoria vazia não repete a consulta automaticamente; selecionar a categoria atual já carregada/em consulta também não. Uma leitura interrompida continua elegível para retomada. Handlers de erro de categorias, listagem e detalhes verificam cancelamento antes de publicar: uma falha de I/O atrasada de uma requisição cancelada não deve reabrir detalhes nem escrever erro no estado novo.

Build Windows debug e consumidores Android concluído; ajuste final protege o foco da busca. Debug f25f5f06abb6c140 entregue no Iniciar após o fechamento da etapa. Sem testes ou interface/player executados; release instalada e versões preservadas.

## Navegação ao vivo e episódios — 02/11/2026

A grade ao vivo também usa ContentGridState compartilhado: recupera canal/chave da família, ID de stream, posição e foco após sair do player; a busca em edição mantém o foco. A restauração consegue localizar o stream dentro de uma família após mudar agrupamento, sem trocar a origem congelada da reprodução.

Nos detalhes de séries, o controlador retém na sessão o último episódio reproduzido (incluindo avanço pelo player) e a temporada. Ao retornar pelo teclado, a lista recupera esse episódio e a temporada visível; setas permitem passar da lista de episódios às temporadas e voltar, seguindo SeriesDetailScreen Android. Os metadados quebram em linhas em vez de disputar largura numa única Row. Isso não é histórico persistente entre sessões nem portabilidade de downloads.

Compilação Windows debug aprovada; builds 23decd15bdb66285 (episódios) e c16c9f4bc4f07c2e (grade ao vivo) entregues no atalho sem interromper processos. Sem execução do app/testes/inspeção visual; release e dados reais preservados.

## Apresentação compartilhada de pôsteres e cabeçalhos — 02/11/2026

PosterCardContent e RatingBadgeContent em uiCommon são o desenho extraído do Android (capa, gradiente, título, foco, nota e progresso quando existente). Android mantém Coil/favoritos e seus tokens responsivos; Windows mantém seu carregador e ações reais. Catálogos e busca Windows usam a mesma apresentação, com a nota dourada Android. Nenhuma ação de favorito ou histórico Windows foi simulada.

PageHeadingText concentra título/subtítulo Android e é usado por AppHeader e cabeçalhos Windows. Arraste e ações ficam nos adaptadores Windows; cabeçalhos com ações passam a duas linhas em pouca largura, para preservar título/busca/ordenação sem alterar o modo da janela. Filtragem e ordenação de Filmes/Séries também usam Dispatchers.Default, descartando projeções canceladas.

Build conjunto Android debug/release e Windows debug aprovado (8 s na extração de pôsteres, 11 s no cabeçalho, fechamento 7 s). Debugs 1f90b77defe81c55, 00efd08c0c14e11a e 1beefb4f97708353 entregues em sequência no Iniciar; o registro local informa o build mais recente. Sem testes ou interface observados em execução, sem publicação ou alteração da release instalada.

## HUD e imagens durante navegação — 02/11/2026

Ao ocultar/minimizar o HUD, o adaptador Windows cancela os estados de clique/arrasto/seek pendente que dependiam de um evento de soltar. A família owner/HUD é reconhecida; uma perda transitória de foco é confirmada no próximo ciclo Swing antes de cancelar. Estado de minimização é observado explicitamente. Mantidos abas, conteúdo dos menus, SwingGraphics, superfície LibVLC e modo da janela. A causa é confirmada por leitura dos handlers, mas a sequência de minimizar/arrastar ainda não foi reproduzida pelo agente. Build debug 95911635cbe9166b entregue.

O Android já usa ImageLoader compartilhado da aplicação; Windows agora mantém carregadores lazily criados por conta durante a navegação, com descarte no logout/troca de conta/fechamento. Catálogo/player reutilizam logos; catálogo/busca reutilizam pôsteres; detalhes têm cache próprio para a resolução maior. Sem armazenamento em disco ou biblioteca nova. Mantidos limites HTTP, cancelamento, tamanho de arquivo e decodificação. Requisições simultâneas da mesma URL compartilham uma trava cancelável e conferem o cache antes de baixar, evitando executar o mesmo download em paralelo; cancelar uma célula não cancela as demais.

Além do máximo de 64 entradas por carregador, o cache limita o custo estimado de pixels (4 MiB logos, 16 MiB pôsteres, 8 MiB detalhes). Isso não mede nem limita o RSS total ou toda a memória nativa. Resultados negativos expiram em 30 s e podem ser tentados na próxima solicitação; falha tardia não substitui imagem válida. Fechamento idempotente impede repovoar cache descartado. Builds debug 50b3f900d8018274 e 72dcd36ef468040e entregues no Iniciar (12 s e 5 s), sem executar o app ou medir rede/memória. Sem testes, publicação ou alteração da release instalada.

## Organização de categorias ao vivo — 02/11/2026

O painel existente passou a Organizar categorias ao vivo: posição, subir/descer/mover ao topo, ocultação, mostrar todas e restauração confirmada do padrão da conta. A lista é virtualizada e acompanha foco/rolagem depois de mover; nomes longos não empurram o controle de visibilidade. Erros de gravação aparecem no próprio painel e a UI só acompanha a nova ordem após persistência confirmada.

CategoryCustomization em shared contém as regras originais Android de aplicar ordem/ocultação e mover IDs preservando categorias temporariamente ausentes. CatalogRepository, ContentCustomizationScreen e SettingsRepository Android delegam a esse mesmo código; conta/tipo, DataStore e dados Android continuam nos adaptadores. Windows salva liveCategoryOrderByAccount com escopo da conta existente e inclui o campo no backup de preferências. Preferências antigas usam ordem padrão, sem migração destrutiva; formatos/limites de gravação continuam validados. A organização de Filmes/Séries Windows continua pendente e não usa IDs ao vivo.

Seleção inicial ao vivo agora aguarda preferências/ordem/ocultação prontas antes de buscar uma categoria. Todas ocultas exibe Mostrar todas, sem reconectar a conta. Categoria vazia carregada é distinta de leitura pendente; clicar novamente não reconsulta. Atualizar categorias faz só a consulta necessária; invalida canais para recarga na próxima entrada quando estiver nas Configurações, preservando seleção válida. Erro tardio de job cancelado de canais/login/categorias não é publicado no novo estado.

Compilação conjunta de shared/Android debug/release e Windows debug aprovada (18 s na extração; fechamento 9 s; últimas correções Windows 8/7 s). Atalho debug mais recente: 0c8d2aa446b908f9, com builds anteriores preservados. git diff --check sem erros. Sem testes adicionados/executados, app/janela/rede observados ou release instalada alterada. Sem publicação, push/tag ou nova versão.


## Fechamento da rodada — Windows 1.22.2

Organizar categorias permite alternar Ao vivo, Filmes e Séries. Ordem e ocultação de Filmes/Séries usam escopos separados de conta+tipo, com defaults vazios para preferências existentes. Sobem/descem/movem ao topo, mostram todas ou restauram a ordem do provedor, usando CategoryCustomization compartilhado com Android. O backup Windows inclui esses mapas, conservando validação e gravação atômica. O painel carrega somente as categorias do tipo escolhido, sem consultar títulos ou preparar metadados globalmente. Ao entrar no catálogo, a seleção aguarda preferências e escolhe a primeira categoria visível na ordem salva; vazio válido e leitura interrompida continuam distintos.

Verificações desta etapa: createDistributable debug e compileDebugKotlin/compileReleaseKotlin Android concluídos; debug a69d66c7bd34670b no Iniciar antes da mudança de versão. git diff --check sem erros. Nenhum teste local acrescentado/executado; interface, provedor, player e upgrade não foram exercitados pelo agente nesta rodada. A publicação não deve ser apresentada como comprovação desses fluxos.

### Adiado para versões futuras

Esta rodada encerra a expansão funcional. Favoritos/Downloads/Multi-view, histórico e retomada persistentes, perfis, categorias por gênero/preparação e compatibilidade de backup/sincronização Android continuam fora desta atualização. A próxima prioridade é revisar o fluxo já existente de reprodução e retorno a partir do feedback da nova versão, antes de portar outro módulo. Não existe paridade completa Android/Windows.


### Publicação e conferência Windows 1.22.2

- Código/tag: ea413eee81fc06665cfe57d40623b409b97c6281 / v1.22.2.1. [CI aprovada](https://github.com/Rafael020703/SquiTV/actions/runs/41558260240): Windows variants e Build and Test (Android/shared/gerador/lint), sem executar instalador release. Os testes foram executados automaticamente no GitHub; não foram executados nem acrescentados testes locais nesta rodada.
- Release estável pública, sem assumir Latest. API pública consultada sem autenticação; exatamente EXE, checksum e manifesto com nomes/URLs/tamanhos/digests esperados. Os três arquivos foram baixados de volta após a publicação e comparados aos bytes do staging. EXE SHA-256: `9792d017a2023dd372bcd8f42c30ed54165781afea2b6a3eb9ff30bd56e43e80`; manifesto: `bf6c40c23515a3a658de2eded993aceab29dab73cee0bfc10dafaa4b9e5f03a5`.
- Referência Windows avançada somente depois da conferência pública; referência Android preservada. Changelog do manifesto contém somente Windows, sem alterar a distribuição Android 1.22/code23.
- Debug 1.22.2 portátil 0f12a95def82e336 entregue no Iniciar com ícone. Nenhum processo do app iniciado/encerrado. Nenhuma instalação, upgrade, visual/player/provedor exercitado nesta rodada; compilação/CI/integridade não comprovam esses fluxos. A instalação release G:/SquiTV e seus dados permanecem intactos.
