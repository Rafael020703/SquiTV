# Squi TV para Windows

## Estado e plataformas

A versão atual do Windows é [**1.26.1 x64**, publicada em 05/11/2026](https://github.com/Rafael020703/SquiTV/releases/tag/v1.26.1.1), em uma atualização exclusiva dessa plataforma. [Notas Windows](releases/v1.26.1-windows.md). O Android permanece em **1.26/code 29**, na [publicação conjunta v1.26](https://github.com/Rafael020703/SquiTV/releases/tag/v1.26), que continua como Latest. As referências estáveis são independentes: Windows aponta v1.26.1.1; Android preserva v1.26. Nenhuma tag ou arquivo anterior foi substituído.

O Windows aproxima funções e apresentação do Android, mas ainda não tem paridade completa. `:desktopApp` consome Kotlin puro em `:shared` e fontes Compose em `uiCommon`; nunca depende de `:app`. Motores, armazenamento e integrações de sistema permanecem específicos de cada plataforma.

A release Windows 1.26.1 distribui `SquiTV-1.26.1.exe`, seu `.sha256` e `squitv-update.json`, com notas somente Windows. Identidade release, integridade dos payloads e propriedades de upgrade/limpeza do MSI foram conferidas sem executar instalador, MSI ou aplicativo; o manifesto passou pela CLI compartilhada. O CI e os três downloads públicos foram conferidos antes de ativar a referência Windows. As verificações e seus limites estão registrados abaixo.

O aplicativo não fornece conteúdo ou credenciais. É necessário um servidor compatível com Xtream Codes. Disponibilidade de metadados, EPG, qualidades e transmissões simultâneas depende do provedor e do aparelho.

## Funções implementadas

| Área | Comportamento atual | Limites |
| --- | --- | --- |
| Login/DNS | Seleção/teste de DNS, conta lembrada opcional e reconexão única | Senhas locais protegidas por DPAPI; logout exige nova entrada manual |
| Sincronização no login (v1.26) | Tela de etapas compartilhada com Android para categorias de canais/filmes/séries; erro, cancelar, tentar novamente e continuar com categorias já obtidas | Títulos/índices continuam sob demanda; não apresenta preparação de metadados/EPG como concluída |
| [DNS do aplicativo](dns-do-aplicativo.md) (v1.26) | Login/dashboard; Google, Cloudflare, Automático com médias em ms e Desativado para comparar com o DNS do aparelho | Escolha por instalação/variante; HTTP/HTTPS LibVLC usa ponte privada quando ativado; execução real ainda não observada |
| Dashboard | Ao vivo, Filmes, Séries, Buscar, Favoritos, Multi-view e Configurações | Notificações não possui ação no Android de referência |
| Ao vivo | Categorias, pesquisa local, agrupamento de versões e qualidade preferida | Troca circular exclusivamente no snapshot da lista de origem, inclusive favoritos/busca |
| Filmes/Séries | Detalhes, temporadas, episódios, favoritos e reprodução | Dados dependem do servidor; episódios não fazem loop ao terminar a série |
| Downloads | Filmes e episódios: baixar, fila de temporada, cancelar, reiniciar, excluir e reproduzir arquivo salvo | Uma transferência por vez; sem HLS/DASH, Smart Downloads ou retomada parcial |
| Histórico | Progresso e retomada de filme/episódio por conta | Continuar assistindo fica nos catálogos/detalhes, nunca na dashboard |
| Categorias | Ordem/ocultação por conta/tipo, contagens e remoção das vazias confirmadas; Todos em Ao vivo e ordenações VOD | Falha de índice mantém contagens desconhecidas; dados de lançamento/nota dependem do servidor |
| Modos VOD | Servidor ou Filtros; gêneros A–Z primeiro, fallback servidor A–Z depois | Preparação de metadados não é instantânea nem baixa vídeo |
| Favoritos | Marcação nos cartões/player, sidebars e biblioteca com busca/filtros por tipo | Metadados ainda dependem do servidor quando não estão em memória |
| Multi-view | Grade 2×2, categorias antes dos canais, busca, agrupamento, áudio de uma tela e reconexão individual | Quatro decodificadores/streams simultâneos não foram observados nesta etapa |
| Player | Áudio, legendas, velocidade, temporizador, Ajustar/Preencher/Zoom e seek VOD compartilhado | Capacidades variam com o stream; EPG somente no player ao vivo comum |
| Configurações | Conta, DNS, reprodução, categorias, backup de preferências e Atualizações | Backup Windows não é intercambiável com Android; histórico/cache não entram nele |

A busca global consulta somente listagens dos tipos escolhidos, com cancelamento, erros parciais e índice de sessão. Não busca episódios individuais nem consulta detalhes/EPG para indexar. Exibe até 300 resultados e pede refinamento quando necessário.

Desde a v1.26, a dashboard só aparece depois da preparação inicial das categorias, ou por continuação explícita após um resultado parcial. Cada entrada/nova tentativa tem uma operação própria; categorias bem-sucedidas são reaproveitadas ao abrir Filmes/Séries. ESC cancela uma carga em andamento; após erro/cancelamento, outro ESC retorna ao login sem mudar fullscreen. Formulário, contas, preferências e metadados persistentes são preservados. A consulta automática de atualização aguarda a saída dessa etapa.

Android e Windows compartilham os estados de sincronização Kotlin e a apresentação Compose. No Android, reentrar na tela solicita uma operação nova mesmo quando a anterior já terminou; falhas mantêm as etapas visíveis e o botão de tentar novamente força a atualização. A navegação automática ao concluir tem uma única origem no coordenador de navegação. Um catálogo autenticado legitimamente vazio não fica preso por exigir pelo menos um título local. DNS passa a iniciar em Automático quando não existe escolha salva, sem sobrescrever uma opção explícita.

Ao vivo/Multi-view carregam um índice de sessão ao entrar explicitamente. Filmes/Séries usam seu índice completo para contar/projetar categorias nos dois modos. Não há varredura global no login/dashboard nem uma requisição por categoria para calcular contagens. A consulta por categoria permanece como recuperação quando o índice falha. Catálogos grandes continuam sujeitos à latência e aos limites do transporte.

**Disponível na v1.24:** Filmes inclui **Adicionados recentemente** nos dois modos de categorias. Reutiliza a regra Android extraída para `:shared`: até 100 títulos dos últimos 14 dias, tolerância de cinco minutos para diferença de relógio, data decrescente e ID decrescente no desempate. Usa o campo `added` da listagem já carregada e respeita categorias ocultas; não consulta detalhes nem coloca histórico na dashboard. A seção desaparece quando não há itens elegíveis. Sem data válida fornecida pelo servidor, não há classificação como recente.

**Disponível na v1.25:** Séries passa a incluir a mesma seção nos modos Servidor e Filtros. O adaptador preserva `last_modified` como data de catálogo, conforme o mapeamento Android; a seleção reutiliza `RecentlyAddedCatalog`, com os mesmos limites dos filmes. Não usa o ano de lançamento nem o progresso de reprodução para classificar séries como recentes. Filtros de pesquisa, contagens, categorias ocultas, favoritos e memória da categoria permanecem no fluxo existente. Sem data válida, a seção não aparece. Compilação concluída; a apresentação e os dados de um provedor não foram observados em execução.

**Disponível na v1.25:** Ao vivo também inclui **Adicionados recentemente**, usando `added` do índice de canais já carregado. Reutiliza a regra Android para selecionar até 100 streams dos últimos 14 dias, filtrando categorias ocultas antes do limite e aplicando depois o agrupamento/qualidade existentes. A contagem representa os cartões agrupados. Busca e Favoritos desta categoria filtram essa seleção; abrir um canal congela exatamente a lista exibida para a troca circular. A categoria e o cartão selecionados usam a memória de navegação existente. Atualizar o catálogo reconstrói a seleção após carregar o índice; não consulta o servidor com o ID sintético `RECENTS` nem substitui a origem de uma reprodução já iniciada. Sem data válida ou itens elegíveis, a seção não aparece. Compilação não comprova UI, dados do provedor ou reprodução em execução.

### Sete avanços de paridade — v1.25

1. **Atualizações:** erro fora da rolagem, com foco na explicação e caminho por teclado até o resumo e a ação. A debug continua sem acesso aos comandos de atualização de produção.
2. **Todos em Ao vivo:** usa o índice carregado, respeita categorias ocultas e agrupamento. A reprodução conserva o snapshot explícito aberto, inclusive após atualização do catálogo; `ALL`/`RECENTS` não são enviados ao servidor como IDs de categoria.
3. **Ordenação VOD:** Nome e Lançamento em Filmes/Séries; Nota nos Filmes. `CatalogOrdering`, extraído da regra Android, é usado pelas duas plataformas. O critério fica na navegação da tela, separado da organização das categorias.
4. **Idiomas do player:** a seleção de áudio/legenda pode registrar o idioma reconhecido, reaplicado nas próximas mídias ao vivo, VOD e Multi-view. Não são persistidos IDs de faixas. Rótulos ambíguos ou sem idioma mantêm apenas a escolha da sessão. Áudio padrão e legendas desativadas limpam a preferência correspondente. A lista de velocidades inclui 1,75× no menu e no adaptador.
5. **Próximo episódio:** aviso com contagem de cinco segundos, Assistir agora e Cancelar. Regra cancelável em `:shared` e apresentação em `uiCommon`, também adotadas pelo Android; cancelar interrompe o trabalho pendente. Windows confere mídia e geração antes do avanço, sem loop no último episódio; controles não cobrem o aviso.
6. **Detalhes:** trailer quando informado, elenco/direção clicáveis e até 15 sugestões da mesma categoria. Trailer abre no navegador em um endereço YouTube HTTPS validado. As obras por pessoa usam somente metadados já carregados na sessão, até 200 resultados; o cache de detalhes é limitado a 256 títulos. Sugestões usam o índice existente ou uma consulta à categoria conhecida, sem varrer detalhes de todo o catálogo. A apresentação ainda não reproduz integralmente a tela Android.
7. **Contas:** Configurações → Conta permite atualizar dados, alternar/remover contas lembradas e entrar com outra conta. Até 32 contas em `accounts.dpapi`, com criptografia do usuário Windows e escrita atômica. A conta lembrada anterior é preservada na lista; remover uma conta salva mantém a sessão aberta, favoritos, histórico e downloads. Lembrar minha conta determina se uma nova entrada é guardada. Defaults debug continuam externos à distribuição e não são incluídos automaticamente na lista.

Os testes locais daquela revisão incluem regras compartilhadas e armazenamento DPAPI com fixtures isoladas. Compilação e testes unitários não comprovam seleção real de faixas, percurso visual de foco, reprodução nem respostas do provedor. Não houve execução de aplicativo ou instalação real durante aquela revisão. Na v1.25, backup seletivo/compatível com Android, sincronização local e recursos avançados de downloads permaneciam pendentes; o backup seletivo Windows foi publicado na v1.26, conforme descrito abaixo.

**Backup com escolha de arquivo — v1.25:** Exportar abre o seletor para salvar um JSON com nome datado; Restaurar permite escolher um backup Windows existente, inclusive o antigo `squitv-backup.json`. Cancelar o seletor não inicia gravação/restauração nem mostra sucesso. Enquanto a operação está pendente, ações duplicadas e fechamento do painel ficam bloqueados; o seletor é descartado ao cancelar sua corrotina. A leitura é limitada a 64 KiB e exige a estrutura dos backups Windows já exportados, rejeitando JSON genérico ou backup Android antes de alterar preferências. Escrita atômica/validação e isolamento por variante permanecem. A restauração publicada na v1.25 substitui todas as preferências, sem prévia ou seleção de grupos. Seletor/foco/fullscreen não foram observados em execução; compiladas as variantes de distribuição e desenvolvimento.

**Backup seletivo — v1.26:** escolher um backup apenas lê e valida o arquivo. O painel mostra nome, contagens de favoritos por tipo e registros de ordem/ocultação; só o clique explícito em Restaurar grava as preferências. Cancelar/Esc descarta a prévia e volta às ações de arquivo sem gravar, com foco inicial em Cancelar na confirmação. A confirmação utiliza o snapshot já validado, sem reler um arquivo que possa ter mudado. As apresentações `BackupRestorePreview` e `BackupChoiceRow` foram extraídas do diálogo Android para `uiCommon` e adotadas pelas duas plataformas, sem compartilhar os formatos incompatíveis de backup. Erros de gravação mantêm a prévia para nova tentativa. Compilação não comprova seletor, foco ou restauração em execução.

Na prévia, **Personalizar** permite escolher Reprodução, Interface e navegação, Categorias e organização, Favoritos e Nomes de exibição. Os grupos presentes no arquivo começam marcados; é possível marcar/desmarcar todos, e Restaurar fica indisponível sem seleção. Backups completos antigos oferecem os cinco grupos; arquivos seletivos oferecem somente os grupos declarados. O merge usa as preferências atuais dentro da escrita serializada: grupos desmarcados ficam intactos. Organização, modos e nomes atualizam somente chaves de conta/tipo presentes no arquivo, preservando outros escopos; favoritos são unidos sem apagar os existentes, conforme a política Android. A memória de navegação integra o grupo Interface e preserva escopos ausentes; IDs legados sem conta continuam sem atribuição. Reprodução/interface selecionadas substituem seus valores globais. O resultado combinado também respeita os limites atuais de quantidade/tamanho, sem truncar dados para forçar uma importação. Compatibilidade Android e transferência local permanecem pendentes.

**Exportação seletiva Windows — v1.26:** Personalizar exportação oferece os mesmos cinco grupos, inicialmente todos marcados, sem iniciar o seletor ou criar arquivo quando a seleção está vazia. O JSON inclui somente os campos dos grupos escolhidos e declara sua disponibilidade no envelope `SquiTV.Windows.Settings`, schemaVersion 1. Na importação, grupos desconhecidos/repetidos, metadados inválidos ou grupos declarados sem todos os campos esperados são rejeitados antes de aplicar; campos omitidos não podem virar preferências padrão e sobrescrever outros grupos. O merge também recusa seleção de grupos ausentes. O limite de 64 KiB, leitura limitada, snapshot validado e gravação atômica permanecem.

O leitor ainda aceita backups Windows completos antigos (`schema: 1`), reconhecidos como contendo todos os grupos. Backups novos, inclusive quando completos, exigem o Windows com suporte ao formato seletivo: o leitor v1.25 rejeita o envelope, evitando interpretar uma exportação parcial como restauração integral. O formato Android continua distinto e não é aceito nesta etapa. Nenhum arquivo de preferências é migrado ou convertido automaticamente. Exportação/importação reais e UI não foram observadas em execução; as verificações de distribuição da v1.26 estão registradas abaixo.


A preparação VOD mantém cache por conta/tipo, lotes, pausas, cache negativo e tentativas limitadas. Pausa fora do catálogo ativo, em reprodução/segundo plano ou por solicitação. Velocidade/pausa do coordenador são de sessão. Não confundir preparação com sincronização das listas.

**Preparação otimizada — v1.25:** Android e Windows compartilham a política Lento (1 consulta), Normal (2) e Rápido (até 5 consultas simultâneas), com intervalo entre lotes de 5/2/1 segundos após as respostas. Não há espera individual adicional ou pausa longa fixa a cada dez itens. Falhas reduzem a concorrência para uma consulta e aumentam o intervalo; três lotes bem-sucedidos restabelecem o ritmo. São consultas individuais limitadas, não uma API capaz de entregar cinco metadados em uma chamada.

O Windows prepara uma fila ordenada em vez de varrer todos os títulos por item; atualiza contadores incrementalmente e agrupa checkpoints/publicação de gêneros em 25 resultados ou ao atingir dez segundos entre verificações no fim de um lote. Pausa/saída normal descarrega resultados já acumulados, mantendo o cache existente. No Android, a fila usa blocos de 50 candidatos, estatísticas agrupadas/incrementais e projeções leves do banco; a conferência completa ocorre na entrada e periodicamente, sem reler capas/sinopses de todo o catálogo por lote. Escritas do lote são transacionais, sem migração/apagamento do banco.

O painel exibe percentual, preparados/total e estimativa restante baseada em pelo menos dois lotes e cinco respostas processadas, usando uma janela de até 20 lotes. Cache anterior e tempo em pausa não contam como amostras; mudar velocidade/reiniciar preparação recalcula a estimativa. Falhas pendentes suspendem a previsão. Falha não conta como concluída; ausência válida de gênero aparece separadamente e mantém cache negativo de sete dias. O total Windows é do catálogo ativo; Android mostra o total de Filmes/Séries e seus contadores separados. Tempo final depende do servidor, respostas e rede; não há ganho medido com os 21 mil conteúdos do usuário nem validação em aparelho nesta etapa.

### Ajustes após feedback Windows — incluídos na v1.24

- **Nome de exibição:** Configurações → Conta permite salvar ou restaurar o nome mostrado na dashboard/conta. A regra de apresentação é compartilhada com o Android em `AccountPresentation`; o login IPTV continua igual. Nomes ficam por conta nas preferências Windows, com limite de 256 caracteres, escrita atômica e confirmação após salvar. Entram no backup Windows existente. Não há migração nem cópia automática dos dados Android.
- **Inicialização/fullscreen:** restaurada pelo evento nativo de exibição da janela e um frame subsequente. O desenho com `isShowing` da tentativa anterior podia deixar a restauração sem disparar; foi retirado. A escolha inicial vem das preferências (padrão `true`), e a última escolha explícita janela/fullscreen é persistida sem usar estados transitórios do peer. F11 atua uma vez ao soltar, inclusive nos HUDs. Valores existentes são preservados; a janela temporária da inicialização não vira preferência. A restauração e a antiga tela preta ainda não foram observadas em execução pelo agente.
- **Mouse:** botão lateral Voltar (botão 4 entregue ao Java) encaminha Esc aos mesmos handlers da janela, diálogo ou HUD focado. Atua uma vez ao soltar; não monitora outros aplicativos. Avançar permanece sem ação por escolha do usuário. Mapeamentos próprios de drivers e a superfície nativa ainda não foram exercitados com mouse físico.
- **Instalador:** após sucesso sem reinicialização pendente, a opção marcada “Abrir o Squi TV ao concluir” permite iniciar no clique Concluir. Em instalação por máquina, o processo original aguarda o filho UAC e abre o executável registrado da mesma variante sem elevação. Se o instalador já foi aberto como administrador, a abertura automática fica desabilitada. Cancelamento/falha não dispara o app; falha ao abrir não desfaz a instalação. Os instaladores foram empacotados; verificações isoladas usam recursos debug, sem executar MSI/EXE.

O desenvolvimento utiliza debug portátil na pasta fixa `SquiTVDebugPortable/current`, com atalho no Iniciar atualizado por `tools/deploy-windows-debug.ps1`. O script preserva dados e processos abertos e evita acumular cópias permanentes. Hashes de builds e evidências de cada entrega ficam no registro local de manutenção, fora do repositório público.

## Downloads de filmes e episódios — v1.24

O cartão Downloads abre uma biblioteca por conta. **Baixar** fica nos detalhes do filme; Séries tem **Baixar episódio** e **Baixar temporada**, usando as temporadas/episódios reais já carregados e as regras compartilhadas de `OnDemandPlayback`. A apresentação da linha e a formatação de tamanhos continuam compartilhadas com Android.

- Uma transferência por vez; temporadas entram numa fila explícita do usuário. Itens concluídos ainda presentes e itens já na fila não são duplicados. Erro de transferência interrompe os pendentes, evitando insistência automática no servidor. Limite de 1.000 registros por variante.
- Fechar o app, sair/trocar de conta ou cancelar a fila interrompe os pendentes. Ao reabrir, não há retomada automática: **Reiniciar começa do zero**. Pedir a mesma temporada novamente pula os arquivos concluídos reconhecidos. Sem serviço em segundo plano ou HTTP Range.
- Conta + tipo + ID identificam o conteúdo; filme e episódio com o mesmo ID usam registros/arquivos distintos. Registros antigos deste backend eram comprovadamente filmes e continuam como tal por campo opcional com valor padrão. Episódios incluem série/temporada/número, sem URL ou credenciais no índice.
- Biblioteca permite excluir e reproduzir arquivo concluído cujo tamanho confere. Uma série local monta a sequência apenas com episódios salvos e verificados da mesma conta/série; anterior/próximo e transição automática usam esse snapshot, sem buscar episódios ausentes por streaming. Arquivo removido após o snapshot gera erro local, sem fallback de rede.
- Arquivos/índice continuam em `%LOCALAPPDATA%/<variante>/downloads`, com UUIDs e gravação atômica. O backup de preferências não transporta os vídeos ou o índice. Quem lembrou a conta pode usar Downloads salvos no login sem nova autenticação; logout explícito não reativa reconexão.
- Transporte separado de IPTV/atualização: até cinco redirecionamentos, sem downgrade HTTPS ou user-info, HTTP 200 e checagem de tamanho quando informado. HTML/JSON/playlists reconhecidas são rejeitadas. HLS/DASH e Smart Downloads não estão implementados.

A implementação foi revisada e compilada; nenhum download, reprodução local ou fila real foi exercitado pelo agente.

## Capas, logos e imagens dos conteúdos — v1.24

- `ContentImages` compartilhado trata espaços, entidades HTML, valores vazios e URLs relativas ao servidor conhecido, sem inventar uma origem para a imagem. DTOs aceitam arte opcional em texto/lista e ignoram formatos inválidos desse campo sem descartar os demais metadados.
- Windows aceita até três redirecionamentos de imagem, mantendo HTTPS, cancelamento, limite de bytes e prazo total de rede de 12 s por candidato. Reutiliza Skia já incluído no Compose para WebP/SVG que ImageIO não reconhece; nenhum componente novo é baixado.
- Quatro carregamentos simultâneos, cache de até 64 entradas por carregador e orçamento de memória por área. Imagem codificada: até 2 MiB para logos, 4 MiB para cartões, 6 MiB para detalhes; SVG até 512 KiB; raster até 16 milhões de pixels, reduzido antes de entrar no cache. Não há ganho de memória ou tempo medido.
- Célula reutilizada limpa imediatamente a arte anterior. Falha transitória visível pode retentar uma vez após o cache negativo expirar. Detalhes usam alternativas reais de capa; a arte recebida ao abrir um detalhe também é aplicada à lista em memória.
- Android mantém Coil/SVG e caches existentes, com normalização/fallback de arte usando a mesma regra; filmes/séries podem usar a capa conhecida se o backdrop falhar. Metadados de uma série não são reaproveitados visualmente para outra. Grades mostram placeholder quando nenhuma imagem é fornecida/carregada.

Servidor sem arte, arquivo inacessível, TLS inválido ou imagem acima dos limites continua sem uma imagem real. Nenhuma requisição a conteúdo de usuário ou execução de decodificação foi feita nesta etapa; compilação não prova que todo recurso de um provedor carregou.

## Player e janela

### Janela e detalhes — Windows 1.26.1

O modo de tela cheia Windows passa a ser **tela cheia em janela**, sem bordas e com os limites do monitor, mantendo o peer AWT em `Floating`; não solicita fullscreen exclusivo nem troca o modo de vídeo. F11 e duplo clique nas áreas não interativas de arraste dos cabeçalhos alternam os modos. O último modo continua salvo por variante e a primeira abertura continua em tela cheia em janela. ESC permanece no fluxo de navegação. No modo janela, o arraste usa as coordenadas de tela do mouse; a proporção 16:9 e o tamanho/posição da sessão são restaurados ao sair da tela cheia, ajustados à área útil do monitor. Em tela cheia, arraste e redimensionamento ficam desativados. Não foi adicionada barra de título ou ação sobre botões/campos.

Detalhes Windows seguem a hierarquia de ações do Android `VodDetailContent`: título, metadados e Assistir/Continuar antes da sinopse. Séries também oferecem acesso ao primeiro episódio da temporada selecionada, reutilizando `OnDemandPlayback`; retomada, temporadas, episódios, favoritos, trailer e downloads permanecem nas regras existentes. A sinopse aparece em um único bloco: Ler mais/Ler menos expande o próprio texto. Direção e elenco ficam em uma seção recolhível. Conteúdos semelhantes continuam disponíveis em cartões horizontais, em seção recolhida ao final, depois dos episódios; não ocupam o espaço anterior à ação de assistir. Capas, espaçamento e largura da coluna de temporadas adaptam-se ao viewport. O foco inicial prioriza a ação principal; ao retornar da reprodução, permanece a restauração do episódio.

Esses ajustes foram compilados, entregues pela debug portátil e publicados no Windows 1.26.1; não foram observados em execução pelo agente. O registro local informa o hash e a entrega atual, sem dados privados no repositório.

- **Volume por setas, v1.24:** com os controles ocultos, esquerda/direita diminuem/aumentam o volume do app em passos de 5%, entre 0 e 100, com indicador por 1,5 s. Uma ação ao soltar a tecla evita repetições acidentais. Com controles ou menus abertos, as setas navegam e continuam atuando no seek quando a barra está focada. Regra de volume e indicador são compartilhados com Android; o Windows conserva o valor nas preferências existentes. O volume Android atua no player Media3 da sessão, sem configurar o áudio da TV.
- **Teclado Windows:** setas movem o foco entre componentes; Enter aciona o componente e Esc volta/fecha o menu pelo fluxo existente. A navegação adicional trata somente eventos que editores, sliders, menus e player não consumiram. Inclui diálogos de configurações/downloads e HUDs nativos, sem alterar F11 ou as regras de fullscreen. Diálogos do sistema continuam usando a navegação do Windows. Compilação não comprova alcance de todos os controles ou geometria do foco em execução.
- **Memória dos catálogos, v1.24:** ao reabrir Ao vivo/Filmes/Séries, restaura categoria e cartão selecionado, sem reproduzir automaticamente. Mantém itens por até 16 categorias em cada escopo, com até 64 escopos/tipos e orçamento de 16 KiB para a memória compartilhada. Usa somente IDs, por conta/tipo; no Android também por perfil. Reutiliza dados carregados e valida a categoria visível antes de restaurar; conteúdo removido/oculto não é reaberto à força. Salva foco estabilizado após 400 ms e clique imediatamente, elimina gravações idênticas e aguarda a gravação pendente no fechamento normal do Windows. Não é histórico de progresso ou sincronização entre aparelhos.
- LibVLC usa superfície nativa; os HUDs mantêm SwingGraphics. No Multi-view Windows o cabeçalho do slot ocupa faixa acima do Canvas para não ficar sob o HWND; ainda difere do overlay Android.
- Ajustar conserva a imagem inteira e a proporção; Preencher pode esticar; Zoom preenche cortando bordas sem esticar. O adaptador limpa operações anteriores e recalcula conforme o viewport. Barras codificadas dentro do vídeo não são detectadas automaticamente.
- VOD compartilha controlador de seek Android: coalescimento de 300 ms, confirmação nativa e proteção por sourceId/generation contra eventos antigos. Pausa fica centralizada. Retomada depende de duração/seekability válidas.
- Movimento contínuo do mouse por 0,5 s revela controles; clique/teclado atuam imediatamente. Esc fecha primeiro um menu aberto; sem menu, retorna da reprodução mesmo com controles visíveis. Nunca altera fullscreen.
- Janela sem barra nativa ou controles por hover. Modo janela 16:9, inicial 1120×630 e mínimo proporcional. Primeira abertura em fullscreen; último modo salvo por variante. F11 alterna e fullscreen bloqueia redimensionamento/arraste.
- Na raiz, Esc/Voltar abre Sair/Ficar, com Ficar focado. Navegação de Voltar ocorre em KeyUp. Sem botões visuais Voltar/Home; sair da conta fica somente nas Configurações.
- Uma instância por variante/sessão Windows, inclusive entre builds portáteis. Debug e release podem coexistir.

## Compilação

Use Windows x64 e JDK 21 completo com `bin/jpackage.exe`. Configure `JAVA_HOME` na sessão; não é necessário mudar o Java do Android Studio.

```powershell
# Desenvolvimento: imagem debug autocontida e atalho no Iniciar
.\gradlew.bat :desktopApp:createDistributable '-PdesktopVariant=debug' --console=plain
.\tools\deploy-windows-debug.ps1

# Produção: empacotar sem executar o instalador
.\gradlew.bat :desktopApp:writeUpdateMetadata '-PdesktopVariant=release' --console=plain
```

`writeUpdateMetadata` gera o EXE online e registra versão, arquitetura, tamanho e SHA-256 reais. Não distribua o MSI intermediário ou a imagem portable como atualização release. Antes de alternar variante, preserve instaladores/metadados concluídos fora das saídas Gradle.

| Variante | Nome | Saída | Dados |
| --- | --- | --- | --- |
| Release | Squi TV | desktopApp/build/release/compose/binaries/main/exe/SquiTV-1.23.0.exe | %LOCALAPPDATA%/SquiTV |
| Debug | Squi TV Debug | desktopApp/build/debug/compose/binaries/main/app/SquiTVDebug | %LOCALAPPDATA%/SquiTVDebug |

UUIDs de upgrade e diretórios de dados são distintos e devem ser preservados. O nome de uma tarefa de minificação não define a identidade debug/release.

### Debug portátil

O deploy mantém uma única imagem em `%LOCALAPPDATA%/SquiTVDebugPortable/current` e atualiza o atalho **Squi TV Debug**, preservando o ícone em caminho físico. O hash identifica o conteúdo, sem gerar outra pasta por compilação. Se o host redirecionou AppData, o script reutiliza o caminho físico do atalho existente, inclusive ao executar o próximo deploy fora desse host. Dados de conta, preferências e downloads continuam fora da imagem, em `%LOCALAPPDATA%/SquiTVDebug`.

Para substituir uma debug já implantada em `current`, feche o aplicativo e execute o deploy novamente. O script não encerra processos nem atualiza arquivos em uso. Copia primeiro para `pending`, promove a imagem pronta e remove `previous` após atualizar o atalho; essas pastas são temporárias, não um histórico de versões. Um lock impede dois deploys simultâneos, e a reserva de instância debug impede uma nova abertura durante a substituição.

Na migração, cópias completas reconhecidas em `builds/<hash>` e backups de atalhos gerados pelo deploy são removidos. Uma cópia antiga ainda em execução é preservada e removida no próximo deploy após seu fechamento. Pastas desconhecidas/incompletas e links não são apagados automaticamente. A release instalada não participa desse processo.

A imagem debug contém Java e VLC; mova a pasta inteira, não apenas o EXE. Dados continuam vinculados ao usuário Windows. Preenchimento de login debug é local, protegido e externo à distribuição; não embutir credenciais em código, JAR ou instalador. A release ignora os defaults e propriedades específicos da debug.

## Instalação e atualizações

**Instalador Windows 1.26.1:** apresentação escura com marca, versão, pasta de destino, etapas separadas de componentes/aplicativo, progresso e conclusão. A pasta pode ser escolhida na própria tela para uma instalação nova; upgrades mostram o destino registrado, sem permitir trocá-lo. A elevação recebe somente o modo fixo e uma pasta local validada; a instalação existente tem prioridade. O MSI é aplicado sem um segundo assistente, mantendo UAC, UUIDs/contextos de upgrade, verificação de componentes, política de limpeza e abertura final sem elevar o aplicativo. O percentual identifica somente a etapa de componentes; a aplicação do MSI usa indicador indeterminado, sem inventar percentual global. Não há WebView, dependência de interface adicional ou novos downloads para desenhar o instalador. Manifesto DPI e rolagem em áreas úteis menores foram incluídos. O EXE debug compilou e o painel foi renderizado fora da tela para conferir contraste/organização; não houve execução do instalador, MSI ou upgrade real. A release instalada permanece intocada. O instalador novo é distribuído no Windows 1.26.1; os assets anteriores permanecem preservados.

- O usuário executa apenas `SquiTV-<versão>.exe`. Java está incluído. O instalador online valida/reutiliza o VLC fixado por hash e baixa/prepara automaticamente somente quando necessário; não carrega VLC arbitrário do sistema.
- Nova instalação por máquina: `C:\Program Files\Squi TV`, com UAC e escolha de pasta. Upgrade preserva destino/contexto existente, inclusive legado por usuário.
- Desinstalação normal remove dados/componentes do aplicativo. Limpeza não ocorre em upgrade: manter a proteção `REMOVE=ALL AND NOT UPGRADINGPRODUCTCODE`. Downloads manuais e registros de manutenção do Windows ficam sob controle do usuário/sistema.
- EXE Windows ainda não possui assinatura Authenticode. SHA-256 acompanha o instalador; o APK Android mantém sua assinatura de distribuição.
- Runtime, licenças e DLLs baixadas não entram no Git. O empacotamento inclui somente vetores Material Extended realmente usados, preservando os bytes originais e a licença.
- Atualizações é visível na debug, com rede/download/instalação de produção bloqueados, inclusive `force=true`. Release usa HTTPS dedicado, hash/tamanho/associação exata e instalação somente por clique explícito. Aviso da dashboard dura 10 segundos em foco.
- Notas são separadas por plataforma. Conjunta usa nova versão menor/tag numérica; Windows exclusivo usa tag técnica com quarta componente e nunca assume Latest. Referências estáveis só avançam após publicação e conferência dos assets. Ver [contrato](contrato-atualizacoes.md), [checklist](checklist-release.md) e [referências](../updates/README.md).

## Dados e manutenção

Preferências, favoritos e organização têm escopo por conta/tipo. Histórico e cache de preparação usam arquivos próprios validados, limites de tamanho e escrita atômica. Arquivo inválido não autoriza apagar dados ou atribuir IDs legados a uma conta por suposição. Perfis/sincronização Android e cache offline completo de favoritos continuam pendentes.

O `.gitignore` conserva planos, auditorias, registros de manutenção, dados privados, caches e builds localmente. Documentação pública permanente e notas de versões são versionadas; binários/checksums/manifesto são assets da release. Não publicar diretórios inteiros de staging nem relatórios contendo caminhos/dados pessoais.

## Evidências e limites

### Revisão Windows 1.26.1

O instalador release x64 1.26.1 foi compilado com JDK 21/jpackage, e o manifesto exclusivo Windows passou pela CLI compartilhada. Os payloads MSI/MST embutidos conferem com os hashes e tamanhos registrados; UUID de upgrade, instalação por máquina e condição de limpeza somente na desinstalação normal foram inspecionados sem executar MSI. O JAR compilado declara DEBUG=false e identidade SquiTV. As fontes distribuídas correspondem à revisão `063b974ef43152c75dfb236d95736233ddd21f63`.

O [CI da revisão distribuída](https://github.com/Rafael020703/SquiTV/actions/runs/42133833684) passou nos dois jobs. Não foram acrescentados nem executados testes locais nesta publicação. Os três downloads públicos conferem por SHA-256/tamanho; EXE de 72.166.400 bytes. A referência Windows só avançou após essa conferência, e Android/Latest v1.26 foram preservados. A prévia do painel do instalador foi renderizada fora da tela na etapa debug. Isso não comprova janela, detalhes, reprodução, UAC, instalação ou upgrade em execução; a release instalada neste PC, seus processos e dados permaneceram intocados.

### Revisão v1.26

Passaram as compilações Android debug/release e do APK de testes instrumentados, lint com zero erros (333 avisos e quatro hints), empacotamento do instalador Windows e validação do manifesto conjunto pela CLI compartilhada. O APK mantém pacote `rsv.squitv`, versão 1.26/código 29, API mínima 24 e ABIs ARM64/ARMv7; o certificado é idêntico ao APK público v1.25, com verificação de assinatura também para API 24. A revisão das fontes distribuídas é `aa58a238283362452215b19f39362ac71bdbb7b7`.

O [CI da revisão distribuída](https://github.com/Rafael020703/SquiTV/actions/runs/42126146259) passou nos dois jobs, incluindo os testes já existentes e verificações isoladas do instalador debug. Não foram acrescentados nem executados testes locais nesta preparação. Os cinco assets públicos da release Latest foram baixados anonimamente e conferidos por tamanho/SHA-256: APK de 7.361.121 bytes e EXE de 72.131.584 bytes. As referências estáveis só avançaram após essa conferência. Compilação, CI e integridade dos arquivos não comprovam DNS, interface, login, reprodução ou upgrade em execução; o APK instrumentado foi somente compilado. A release instalada neste PC, seus processos e dados permaneceram intocados.

### Revisão v1.25

Na revisão local passaram 330 testes Android, 35 compartilhados, 18 Windows debug e dez dos geradores Python. Foram compilados APKs debug/release e de testes instrumentados, com R8/assinatura na release, lint sem erros e instaladores/imagem portátil Windows. A assinatura Android é idêntica ao APK público v1.24.2. As verificações isoladas do instalador debug cobrem componentes, destino/contexto de upgrade e limpeza restrita às fixtures; nenhum instalador ou aplicativo foi executado. Os cinco assets baixados do rascunho conferiram com os bytes preparados.

A revisão corrigiu também o isolamento dos testes da lista de contas e o uso de identidade antiga após suspensão no evento de término Android. Catálogo/Room, progresso, ETA, política de lotes e cancelamento do próximo episódio receberam regressões automatizadas.

O [CI da revisão distribuída](https://github.com/Rafael020703/SquiTV/actions/runs/41768936003) passou nos dois jobs. Após publicar, os cinco assets foram baixados anonimamente e conferidos por tamanho/SHA-256, junto da tag e da condição Latest: APK de 7.332.897 bytes e instalador de 71.943.168 bytes. As referências estáveis só foram avançadas depois dessa conferência. Interface, reprodução, respostas de provedor e instalação/upgrade real não foram executados nesta revisão; os testes instrumentados foram somente compilados.

### Histórico da v1.24

A v1.24 concluiu compilações Android debug/release, empacotamento Windows, lint com zero erros (327 avisos e quatro hints), validação do contrato e assinatura Android idêntica à v1.23. Os cinco assets públicos foram baixados e comparados: APK de 7.322.061 bytes e instalador Windows de 71.787.520 bytes. Naquela publicação, as referências estáveis Android/Windows apontaram o manifesto v1.24, com notas por plataforma. [CI da revisão distribuída](https://github.com/Rafael020703/SquiTV/actions/runs/41700025822); os dois jobs passaram, incluindo os testes já existentes e as verificações isoladas do instalador debug. Esse resultado remoto é separado das compilações locais.

Compilar APK de teste instrumentado não significa executá-lo. Na etapa v1.24 não foram executados testes locais nem observados interface/reprodução/Multi-view ou upgrade da release instalada. Testes executados pelo CI remoto são evidência separada das verificações locais.

Execução, inspeção e verificações de instalação neste PC usam somente debug. A release instalada, seus processos e dados não podem ser abertos, encerrados, reinstalados ou alterados pelo agente. Sem ganhos de FPS/memória/abertura anunciados sem medição.

Histórico técnico detalhado e hashes de debug ficam nos registros locais ignorados; notas públicas de cada distribuição ficam em [releases](releases/).

## Reprodução e consumo de recursos — v1.24

O worker LibVLC mantém a geração exata do pedido durante inicialização/stop potencialmente bloqueantes; não atribui um estado antigo à mídia nova. Comandos de mídia ainda na fila são descartados quando a geração muda. Erros antigos não substituem o estado atual. Ao terminar/falhar, suspende o polling até nova reprodução explícita; getters nativos ficam fora do bloco de atualização repetível do StateFlow. Volume idêntico não gera chamada nativa redundante. O fechamento tolera eventos tardios de AWT/Compose sem reenfileirar ações no executor encerrado.

O histórico Windows evita revalidar todas as entradas para uma posição/duração idêntica ou um término já removido. Android serializa escritas de progresso, ignora repetições não forçadas, preserva CancellationException e contabiliza o intervalo pelo relógio monotônico somente após sucesso. O ticker publica sua amostra antes de aguardar Room e a gravação final captura tipo/série da sessão. Não houve mudança nos buffers, codecs, aceleração, superfície de vídeo ou endpoints. Não há medição de ganho de FPS, RAM ou latência.
