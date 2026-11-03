# Squi TV para Windows

## Estado e plataformas

A versão conjunta [**v1.24**, publicada em 03/11/2026](https://github.com/Rafael020703/SquiTV/releases/tag/v1.24), reúne Android **1.24/code 25** e Windows **1.24.0 x64**. Notas: [Android](releases/v1.24-android.md) e [Windows](releases/v1.24-windows.md). É a versão Latest para clientes Android legados também. Publicações anteriores permanecem no histórico, sem substituir tags ou assets.

O Windows aproxima funções e apresentação do Android, mas ainda não tem paridade completa. `:desktopApp` consome Kotlin puro em `:shared` e fontes Compose em `uiCommon`; nunca depende de `:app`. Motores, armazenamento e integrações de sistema permanecem específicos de cada plataforma.

O aplicativo não fornece conteúdo ou credenciais. É necessário um servidor compatível com Xtream Codes. Disponibilidade de metadados, EPG, qualidades e transmissões simultâneas depende do provedor e do aparelho.

## Funções implementadas

| Área | Comportamento atual | Limites |
| --- | --- | --- |
| Login/DNS | Seleção/teste de DNS, conta lembrada opcional e reconexão única | Senhas locais protegidas por DPAPI; logout exige nova entrada manual |
| Dashboard | Ao vivo, Filmes, Séries, Buscar, Favoritos, Multi-view e Configurações | Notificações não possui ação no Android de referência |
| Ao vivo | Categorias, pesquisa local, agrupamento de versões e qualidade preferida | Troca circular exclusivamente no snapshot da lista de origem, inclusive favoritos/busca |
| Filmes/Séries | Detalhes, temporadas, episódios, favoritos e reprodução | Dados dependem do servidor; episódios não fazem loop ao terminar a série |
| Downloads | Filmes e episódios: baixar, fila de temporada, cancelar, reiniciar, excluir e reproduzir arquivo salvo | Uma transferência por vez; sem HLS/DASH, Smart Downloads ou retomada parcial |
| Histórico | Progresso e retomada de filme/episódio por conta | Continuar assistindo fica nos catálogos/detalhes, nunca na dashboard |
| Categorias | Ordem/ocultação por conta/tipo, contagens e remoção das vazias confirmadas | Falha de índice mantém contagens desconhecidas; Recentes em Séries/Ao vivo e algumas ordenações Android ainda faltam |
| Modos VOD | Servidor ou Filtros; gêneros A–Z primeiro, fallback servidor A–Z depois | Preparação de metadados não é instantânea nem baixa vídeo |
| Favoritos | Marcação nos cartões/player, sidebars e biblioteca com busca/filtros por tipo | Metadados ainda dependem do servidor quando não estão em memória |
| Multi-view | Grade 2×2, categorias antes dos canais, busca, agrupamento, áudio de uma tela e reconexão individual | Quatro decodificadores/streams simultâneos não foram observados nesta etapa |
| Player | Áudio, legendas, velocidade, temporizador, Ajustar/Preencher/Zoom e seek VOD compartilhado | Capacidades variam com o stream; EPG somente no player ao vivo comum |
| Configurações | Conta, DNS, reprodução, categorias, backup de preferências e Atualizações | Backup Windows não é intercambiável com Android; histórico/cache não entram nele |

A busca global consulta somente listagens dos tipos escolhidos, com cancelamento, erros parciais e índice de sessão. Não busca episódios individuais nem consulta detalhes/EPG para indexar. Exibe até 300 resultados e pede refinamento quando necessário.

Ao vivo/Multi-view carregam um índice de sessão ao entrar explicitamente. Filmes/Séries usam seu índice completo para contar/projetar categorias nos dois modos. Não há varredura global no login/dashboard nem uma requisição por categoria para calcular contagens. A consulta por categoria permanece como recuperação quando o índice falha. Catálogos grandes continuam sujeitos à latência e aos limites do transporte.

**Disponível na v1.24:** Filmes inclui **Adicionados recentemente** nos dois modos de categorias. Reutiliza a regra Android extraída para `:shared`: até 100 títulos dos últimos 14 dias, tolerância de cinco minutos para diferença de relógio, data decrescente e ID decrescente no desempate. Usa o campo `added` da listagem já carregada e respeita categorias ocultas; não consulta detalhes nem coloca histórico na dashboard. A seção desaparece quando não há itens elegíveis. Sem data válida fornecida pelo servidor, não há classificação como recente. Séries e Ao vivo permanecem para etapas posteriores.

A preparação VOD mantém cache por conta/tipo, uma consulta de metadados por vez, lotes, pausas, cache negativo e tentativas limitadas. Pausa fora do catálogo ativo, em reprodução/segundo plano ou por solicitação. Velocidade/pausa do coordenador são de sessão. Não confundir preparação com sincronização das listas.

### Ajustes locais após feedback Windows — ainda não publicados

- **Nome de exibição:** Configurações → Conta permite salvar ou restaurar o nome mostrado na dashboard/conta. A regra de apresentação é compartilhada com o Android em `AccountPresentation`; o login IPTV continua igual. Nomes ficam por conta nas preferências Windows, com limite de 256 caracteres, escrita atômica e confirmação após salvar. Entram no backup Windows existente. Não há migração nem cópia automática dos dados Android.
- **Inicialização/fullscreen:** restaurada pelo evento nativo de exibição da janela e um frame subsequente. O desenho com `isShowing` da tentativa anterior podia deixar a restauração sem disparar; foi retirado. A escolha inicial vem das preferências (padrão `true`), e a última escolha explícita janela/fullscreen é persistida sem usar estados transitórios do peer. F11 atua uma vez ao soltar, inclusive nos HUDs. Valores existentes são preservados; a janela temporária da inicialização não vira preferência. A restauração e a antiga tela preta ainda não foram observadas em execução pelo agente.
- **Mouse:** botão lateral Voltar (botão 4 entregue ao Java) encaminha Esc aos mesmos handlers da janela, diálogo ou HUD focado. Atua uma vez ao soltar; não monitora outros aplicativos. Avançar permanece sem ação por escolha do usuário. Mapeamentos próprios de drivers e a superfície nativa ainda não foram exercitados com mouse físico.
- **Instalador:** após sucesso sem reinicialização pendente, a opção marcada “Abrir o Squi TV ao concluir” permite iniciar no clique Concluir. Em instalação por máquina, o processo original aguarda o filho UAC e abre o executável registrado da mesma variante sem elevação. Se o instalador já foi aberto como administrador, a abertura automática fica desabilitada. Cancelamento/falha não dispara o app; falha ao abrir não desfaz a instalação. Apenas o instalador debug foi empacotado, sem executar MSI/EXE.

Debug portátil atual no Iniciar: `00850f5d63b6411c`, na pasta fixa `SquiTVDebugPortable/current`, com as fontes da v1.24. Compilações e deploy concluídos, sem pastas de histórico extras. Nenhum app/player/instalador foi aberto ou encerrado; a release instalada em G:/SquiTV e seus dados foram preservados. Os hashes identificam o build, sem criar cópias permanentes.

## Downloads de filmes e episódios — v1.24

O cartão Downloads abre uma biblioteca por conta. **Baixar** fica nos detalhes do filme; Séries tem **Baixar episódio** e **Baixar temporada**, usando as temporadas/episódios reais já carregados e as regras compartilhadas de `OnDemandPlayback`. A apresentação da linha e a formatação de tamanhos continuam compartilhadas com Android.

- Uma transferência por vez; temporadas entram numa fila explícita do usuário. Itens concluídos ainda presentes e itens já na fila não são duplicados. Erro de transferência interrompe os pendentes, evitando insistência automática no servidor. Limite de 1.000 registros por variante.
- Fechar o app, sair/trocar de conta ou cancelar a fila interrompe os pendentes. Ao reabrir, não há retomada automática: **Reiniciar começa do zero**. Pedir a mesma temporada novamente pula os arquivos concluídos reconhecidos. Sem serviço em segundo plano ou HTTP Range.
- Conta + tipo + ID identificam o conteúdo; filme e episódio com o mesmo ID usam registros/arquivos distintos. Registros antigos deste backend eram comprovadamente filmes e continuam como tal por campo opcional com valor padrão. Episódios incluem série/temporada/número, sem URL ou credenciais no índice.
- Biblioteca permite excluir e reproduzir arquivo concluído cujo tamanho confere. Uma série local monta a sequência apenas com episódios salvos e verificados da mesma conta/série; anterior/próximo e transição automática usam esse snapshot, sem buscar episódios ausentes por streaming. Arquivo removido após o snapshot gera erro local, sem fallback de rede.
- Arquivos/índice continuam em `%LOCALAPPDATA%/<variante>/downloads`, com UUIDs e gravação atômica. O backup de preferências não transporta os vídeos ou o índice. Quem lembrou a conta pode usar Downloads salvos no login sem nova autenticação; logout explícito não reativa reconexão.
- Transporte separado de IPTV/atualização: até cinco redirecionamentos, sem downgrade HTTPS ou user-info, HTTP 200 e checagem de tamanho quando informado. HTML/JSON/playlists reconhecidas são rejeitadas. HLS/DASH e Smart Downloads não estão implementados.

A implementação foi revisada e compilada; nenhum download, reprodução local ou fila real foi exercitado pelo agente.

## Capas, logos e imagens dos conteúdos — alteração local

- `ContentImages` compartilhado trata espaços, entidades HTML, valores vazios e URLs relativas ao servidor conhecido, sem inventar uma origem para a imagem. DTOs aceitam arte opcional em texto/lista e ignoram formatos inválidos desse campo sem descartar os demais metadados.
- Windows aceita até três redirecionamentos de imagem, mantendo HTTPS, cancelamento, limite de bytes e prazo total de rede de 12 s por candidato. Reutiliza Skia já incluído no Compose para WebP/SVG que ImageIO não reconhece; nenhum componente novo é baixado.
- Quatro carregamentos simultâneos, cache de até 64 entradas por carregador e orçamento de memória por área. Imagem codificada: até 2 MiB para logos, 4 MiB para cartões, 6 MiB para detalhes; SVG até 512 KiB; raster até 16 milhões de pixels, reduzido antes de entrar no cache. Não há ganho de memória ou tempo medido.
- Célula reutilizada limpa imediatamente a arte anterior. Falha transitória visível pode retentar uma vez após o cache negativo expirar. Detalhes usam alternativas reais de capa; a arte recebida ao abrir um detalhe também é aplicada à lista em memória.
- Android mantém Coil/SVG e caches existentes, com normalização/fallback de arte usando a mesma regra; filmes/séries podem usar a capa conhecida se o backdrop falhar. Metadados de uma série não são reaproveitados visualmente para outra. Grades mostram placeholder quando nenhuma imagem é fornecida/carregada.

Servidor sem arte, arquivo inacessível, TLS inválido ou imagem acima dos limites continua sem uma imagem real. Nenhuma requisição a conteúdo de usuário ou execução de decodificação foi feita nesta etapa; compilação não prova que todo recurso de um provedor carregou.

## Player e janela

- **Alteração local, ainda não publicada:** com os controles ocultos, esquerda/direita diminuem/aumentam o volume do app em passos de 5%, entre 0 e 100, com indicador por 1,5 s. Uma ação ao soltar a tecla evita repetições acidentais. Com controles ou menus abertos, as setas navegam e continuam atuando no seek quando a barra está focada. Regra de volume e indicador são compartilhados com Android; o Windows conserva o valor nas preferências existentes. O volume Android atua no player Media3 da sessão, sem configurar o áudio da TV.
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

A v1.24 concluiu compilações Android debug/release, empacotamento Windows, lint com zero erros (327 avisos e quatro hints), validação do contrato e assinatura Android idêntica à v1.23. Os cinco assets públicos foram baixados e comparados: APK de 7.322.061 bytes e instalador Windows de 71.787.520 bytes. As referências estáveis Android/Windows apontam o manifesto v1.24, com notas por plataforma. [CI da revisão distribuída](https://github.com/Rafael020703/SquiTV/actions/runs/41700025822); os dois jobs passaram, incluindo os testes já existentes e as verificações isoladas do instalador debug. Esse resultado remoto é separado das compilações locais.

Compilar APK de teste instrumentado não significa executá-lo. Não foram executados testes locais nesta rodada nem observados interface/reprodução/Multi-view ou upgrade da release instalada. Testes executados pelo CI remoto são evidência separada das verificações locais.

Execução, inspeção e verificações de instalação neste PC usam somente debug. A release instalada, seus processos e dados não podem ser abertos, encerrados, reinstalados ou alterados pelo agente. Sem ganhos de FPS/memória/abertura anunciados sem medição.

Histórico técnico detalhado e hashes de debug ficam nos registros locais ignorados; notas públicas de cada distribuição ficam em [releases](releases/).

## Reprodução e consumo de recursos — v1.24

O worker LibVLC mantém a geração exata do pedido durante inicialização/stop potencialmente bloqueantes; não atribui um estado antigo à mídia nova. Comandos de mídia ainda na fila são descartados quando a geração muda. Erros antigos não substituem o estado atual. Ao terminar/falhar, suspende o polling até nova reprodução explícita; getters nativos ficam fora do bloco de atualização repetível do StateFlow. Volume idêntico não gera chamada nativa redundante. O fechamento tolera eventos tardios de AWT/Compose sem reenfileirar ações no executor encerrado.

O histórico Windows evita revalidar todas as entradas para uma posição/duração idêntica ou um término já removido. Android serializa escritas de progresso, ignora repetições não forçadas, preserva CancellationException e contabiliza o intervalo pelo relógio monotônico somente após sucesso. O ticker publica sua amostra antes de aguardar Room e a gravação final captura tipo/série da sessão. Não houve mudança nos buffers, codecs, aceleração, superfície de vídeo ou endpoints. Não há medição de ganho de FPS, RAM ou latência.
