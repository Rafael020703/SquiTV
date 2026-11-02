# Windows — início do aplicativo

## Escopo atual

Windows x64: login Xtream, conta ativa/validade, dashboard, categorias/busca de canais, filmes e séries, detalhes de filmes, temporadas/episódios, reprodução integrada com LibVLC e configurações iniciais. Catálogos e preferências Windows ainda não têm paridade completa com o Android.

Ao abrir Ao vivo, a primeira categoria é selecionada; as demais são consultadas sob demanda. Não há consulta global automática. A lista visível ao abrir o canal é congelada para anterior/próximo, incluindo o filtro de busca. A navegação é circular nessa origem, sem fallback global. Ao encerrar o player, busca e posição da lista são preservadas na sessão. Favoritos, histórico/progresso persistente, categorias por gênero/preparação, sincronização e instalador de atualizações Windows ainda não foram portados.

O núcleo `:shared` fornece modelos/parser Xtream, DNS, cores, índice circular, controlador de interação do player, montagem de endereço de canal e contrato de distribuição. Android e Windows usam as regras portáveis sem dependência entre aplicações. O desenho do launcher Android foi extraído para `buildSrc/BrandIcon`; dele saem também PNG/ICO Windows. Os ícones de ações usam a mesma família Material Rounded. Logos de canais vêm do servidor, com cache limitado a 64 imagens e até quatro consultas simultâneas.

## Dashboard — adaptação em andamento

A apresentação dos cartões e o fundo usam `CinematicCardContent`/`PortalBackdrop` extraídos do Android para uiCommon, com as mesmas regras numéricas de viewport 1280×720 em shared/ViewportSizing. Windows adiciona hover/mouse e percurso por setas, foco inicial em Ao vivo e restauração de foco/rolagem ao retornar dos catálogos. O estado da dashboard é removido na saída da conta. O relógio HH:mm atualiza no início de cada minuto.

Ao vivo, Filmes, Séries e Configurações têm destinos reais. Busca, Favoritos, Downloads e Multi-view ainda exigem portabilidade funcional; os cartões/ícones dessas áreas continuam visuais. O botão de Notificações também não tem ação implementada no Android atual. Não confundir aparência compartilhada com paridade funcional concluída.

A sequência solicitada é concluir dashboard, depois adaptar o player de filmes/séries e então implementar o fluxo de atualização **somente da release Windows**, com apresentação Android, manifesto compartilhado e aviso de 10 segundos. Essas duas últimas etapas ainda não foram implementadas. A debug permanece separada das atualizações de produção.

## Build

JDK 21 completo, com `bin/jpackage.exe`, e Windows x64. Definir `JAVA_HOME` para esse JDK na sessão. Não é necessário mudar o Java do Android Studio. O plugin Compose obtém suas ferramentas de empacotamento.

```powershell
# Release Windows (padrão)
.\gradlew.bat :desktopApp:createDistributable :desktopApp:packageExe --console=plain

# Debug Windows, instalável ao lado da release
.\gradlew.bat :desktopApp:createDistributable :desktopApp:packageExe '-PdesktopVariant=debug' --console=plain
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
| Release | SquiTV-1.21.1.exe | desktopApp/build/release/compose/binaries/main/exe | %LOCALAPPDATA%/SquiTV |
| Debug | SquiTVDebug-1.21.1.exe | desktopApp/build/debug/compose/binaries/main/exe | %LOCALAPPDATA%/SquiTVDebug |

Cada variante tem nome no menu Iniciar e UUID de upgrade próprios. Preservar os UUIDs no Gradle em atualizações futuras. `packageExe` é o instalador da variante escolhida por `desktopVariant`; os nomes de tarefas de minificação do plugin não definem a identidade debug/release do aplicativo.

Ao alternar `desktopVariant`, tarefas com a mesma identidade Gradle podem limpar saídas antigas. Para conservar os dois instaladores, copie cada `.exe` concluído antes de compilar a outra variante. Os builds desta etapa foram preservados em `.artifacts/windows/release` e `.artifacts/windows/debug`, ignorados pelo Git. Isso não afeta a separação das aplicações já instaladas.

Os instaladores incluem Java e bibliotecas; o usuário final executa apenas o instalador `.exe`. A pasta `main/app` contém a imagem sem instalador, útil para desenvolvimento. Os executáveis iniciais não têm assinatura Authenticode.

### Runtime de vídeo

`prepareVlcRuntime` obtém o ZIP oficial VLC 3.0.23 Windows x64, verifica o SHA-256 fixado no código e prepara DLLs/plugins/licenças como recursos da aplicação. O arquivo baixado fica em `.artifacts/native`, fora do Git; o runtime extraído fica em `build/desktopNativeResources`. O usuário não precisa instalar VLC separadamente. A procedência é registrada em `SQUI-TV-RUNTIME.txt` junto às bibliotecas. Não remover avisos/licenças na distribuição.

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

A janela não exibe moldura, título nativo, nome/debug/versão internos ou faixa reservada para controles. A faixa de 28 dp e os botões por hover foram removidos após a correção solicitada pelo usuário; não os recolocar. Também foram removidas as margens globais vazias. O perfil na dashboard, o cabeçalho das listas e a área institucional do login permitem arrastar a janela sem uma barra adicional. Início/Sair da conta ficam nos cabeçalhos existentes.

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

O menu segue a organização Android (abas adaptadas por largura): Imagem, Áudio, Legendas, Velocidade e Temporizador. Proporção original/16:9/4:3, faixas anunciadas pelo LibVLC, legendas desativáveis, velocidades suportadas e temporizador têm comandos nativos/ação efetiva; falhas de escolha são apresentadas sem encerrar o vídeo. A seleção de faixas depende da transmissão e pode ser atualizada no menu. Agrupamento/seletor de qualidade, favoritos, PiP e outros detalhes Android ainda não têm paridade completa.

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
| Categorias e listagem ao vivo | Grade de cartões quadrados com logos, cabeçalho e sidebar baseados no Android; agrupamento, favoritos e personalização pendentes |
| Player, EPG dentro da reprodução, áudio, legendas, proporção e temporizador | Ao vivo com HUD, EPG, controles, menu nativo de faixas/proporção/velocidade/temporizador implementados; comportamento final ainda sem validação visual. Paridade completa pendente |
| Troca circular restrita à categoria/lista de origem | Implementada com índice compartilhado e snapshot da lista visível; favoritos ainda não disponíveis no Windows |
| Filmes, detalhes, atores, categorias servidor/gênero e preparação | Catálogo do servidor, busca, detalhes textuais e reprodução iniciais implementados; navegação por ator/gênero, preparação e biblioteca persistente pendentes |
| Séries, temporadas, episódios e continuação | Catálogo/detalhes, temporadas, episódios, reprodução e sequência automática iniciais implementados; histórico/retomada persistente pendentes |
| Favoritos, histórico, progresso e perfis por conta | Pendentes; preservar escopos e formatos Android |
| Busca global, downloads e multiview | Pendentes |
| Configurações, categorias ocultas/ordem personalizada e idiomas/zoom | Grade inicial com conta, DNS, zoom, auto-reprodução e logout; demais opções pendentes |
| Importação/exportação seletiva e sincronização local | Pendentes; manter contrato compatível com Android |
| Atualizações Windows | Tela nas Configurações em ambas as variantes; rede/download/instalação habilitados somente na release. Fluxo compilado/empacotado, sem atualização real exercitada |
| Login pela rede local | Pendente |
| PiP e reprodução em segundo plano | Pendentes de adaptação nativa; manter funções distintas |

Orientação atual do usuário (02/11/2026): portar **visual e funções** de Filmes, Séries e Configurações, reaproveitando o código Android. A restrição anterior de somente visual foi substituída por esse pedido expresso. A prioridade mais recente é player de filmes/séries → atualizações Windows → dashboard. Player e atualizador iniciais já estão implementados; a paridade funcional completa continua pendente. Manter a continuidade incremental, uma parte por execução agendada. Os demais botões ainda sem função da dashboard continuam visuais por autorização anterior; sua presença não comprova portabilidade. Não declarar concluída a paridade completa enquanto as pendências da tabela existirem.

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

## Player de filmes/séries e Atualizações — 02/11/2026

O VOD reaproveita PlayerOverlayController, PlayerSeekController e CenteredPlayerActions do Android. Controles ocultam após 5 segundos de inatividade, mantêm pausa central e suspendem ocultação durante seek/menu/interação contínua. Seek por teclado é coalescido em 300 ms, cancelável por Esc/Voltar; duração e seekability vêm do player. A confirmação do alvo considera a direção para não aceitar posição antiga em saltos para trás. Filmes têm três controles centrais; séries têm cinco incluindo episódio anterior/próximo, além de lista de episódios do snapshot atual. Não há loop entre último e primeiro episódio. sourceId/generation evitam usar ended de uma reprodução anterior. Temporada do episódio reproduzido é selecionada ao retornar. Áudio, legendas, imagem, velocidade e temporizador usam o menu e comandos LibVLC existentes. Histórico/retomada entre sessões permanecem pendentes.

Configurações → Atualizações fica visível nas duas variantes. Debug exibe a versão instalada e estado de desenvolvimento, sem consultar, baixar ou instalar produção. Release oferece verificação, novidades, download com progresso/cancelamento, repetição após falha e instalação explicitamente confirmada pelo botão “Instalar e fechar o app”. A abertura do instalador fecha o app apenas depois do lançamento aceito pelo Windows. Não existe instalação automática pela rotina.

A tela usa os mesmos painéis de status/versões Android extraídos para uiCommon, largura máxima de 720 dp, etapas Baixar/Verificar/Instalar, ações legíveis e novidades recolhíveis. O filtro de notas releaseHighlights mantém a implementação compartilhada, sem expor seções técnicas de download/checksum. O aviso da dashboard reutiliza UpdateNoticeContent, com 10 segundos em primeiro plano e opção de ignorar uma versão.

WindowsUpdateRepository usa cliente público GitHub separado do IPTV, cancelamento de chamadas/corpos, limites de tempo/tamanho, HTTPS e redirects limitados a hosts GitHub/CDN autorizados. Confere manifesto schema v1, associação exata tag/asset/URL/tamanho e concordância SHA-256 com digest/sidecar disponíveis. Seleciona apenas EXE canônico release de versão superior e arquitetura compatível. Verifica tamanho, cabeçalho MZ e SHA-256 antes de disponibilizar/abrir o instalador. O hash comprova integridade com o manifesto, não assinatura Authenticode do editor; instaladores atuais não possuem essa assinatura. Cache de descoberta de 12 h somente na sessão para resultados bem-sucedidos; erros permitem nova tentativa. A preferência ignorada é isolada por variante.

Sair da conta aparece exclusivamente nas Configurações. Removidos ícones Home/Início dos cabeçalhos/listagem; Esc/Voltar mantém o fluxo de retorno e, na tela de Atualizações, retorna às Configurações. F11 continua controlando tela cheia, independentemente de Esc. Não foram acrescentados botões visuais de voltar.

Verificação desta etapa: fontes compartilhadas compiladas para Android debug/release; Windows debug portátil gerada e atalho do Iniciar atualizado; EXE release empacotado localmente. Nenhum teste acrescentado/executado, nenhuma janela aberta/encerrada pelo agente, nenhum novo controle/player/fluxo de atualização observado em execução. Canais ao vivo funcionando é relato anterior do usuário. Não houve push, publicação, instalação ou mudança de versão. tools/generate_manifest.py permanece dedicado ao Android; prepare_release.py agora prepara o manifesto conjunto, as notas por plataforma e os candidatos de referência estável. writeUpdateMetadata vincula o EXE real a versão/arquitetura/hash/tamanho. Publicação e avanço das referências continuam pendentes de autorização.

### Distribuição por plataforma

Candidata conjunta local: Android 1.22/code 23 e Windows 1.22.0, tag futura v1.22. Correções isoladas podem usar o mesmo número em cada app, por exemplo Android 1.22.1 e Windows 1.22.1; tags futuras v1.22.1 e v1.22.1.1 evitam colisão sem invalidar Android antigos. O título Windows mantém v1.22.1, sem a componente técnica da tag.

Changelog novo vem da chave windows do manifesto; Android lê android. Fallback de body preservado somente em manifestos antigos de plataforma única. Ao esgotar histórico ou limite de manifestos, a referência fixa Windows resolve sua própria release por tag/hash, independentemente de quantas Android foram publicadas. Referências candidatas são geradas localmente e só entram em updates/stable depois de existir a release real.
