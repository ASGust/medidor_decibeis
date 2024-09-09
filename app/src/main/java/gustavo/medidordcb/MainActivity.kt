package gustavo.medidordcb

// mín = 0 dB, máx = 120 dB para fins de visualização


import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import gustavo.medidordcb.theme.SoundMeterESPTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    companion object {
        val TAG = MainActivity::class.simpleName

        private val PROGRESS_BAR_HEIGHT = 50.dp
        private val PROGRESS_BAR_WIDTH = 200.dp

        private var isRunning = false   // usado no lugar do MeterService.isRecording para prevenir condições de corrida

        var coldStart = true

        fun dBToProgress(dB : Float) : Float {
            return dB/120 // escala de [0dB-120dB] para [0-1]
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantém a tela ligada enquanto o aplicativo está em execução, além de usar wakelock (impede que a tela apague automaticamente)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // manter a tela ligada (além do wakelock)
        // Exibe uma mensagem de log para indicar que o método onCreate foi chamado
        Log.d(TAG, "onCreate!")

        // Registrando um callback que chama o método finish() quando o botão de voltar é pressionado.
        this.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Cria uma intenção para parar o serviço MeterService
                val i = Intent(applicationContext, ServicoMedicao::class.java)
                stopService(i)
                coldStart = true // Marca o coldStart como verdadeiro (para futuras execuções)
                finish() // Fecha a atividade atual
            }
        })

        Log.d(TAG, "onCreate: coldStart = $coldStart")

        setContent {
            SoundMeterESPTheme {
                // Um contêiner de superfície usando a cor 'background' do tema
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Verifica o estado da permissão para gravar áudio
                    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
                    if (coldStart){   // Reseta todos os valores de configuração
                        Valores.resetAll()
                        if(permissionState.status.isGranted) {  // Se a permissão para gravar áudio foi concedida
                            // Cria uma intenção para iniciar o serviço MeterService em primeiro plano
                            val i = Intent(applicationContext, ServicoMedicao::class.java)
                            startForegroundService(i)
                            isRunning = true // Marca que o serviço está em execução
                            Log.d(TAG, "onCreate: Serviço iniciado")
                        }
                    }
                    AppContent(permissionState) // Chama a função que desenha o conteúdo da aplicação na tela
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause!")
        val i = Intent(applicationContext, ServicoMedicao::class.java)// Cria uma intenção para o serviço MeterService
        val wasRecording = isRunning // Armazena o estado de gravação atual
        stopService(i)  // Para o serviço MeterService
        // Se o serviço estava gravando e a atividade não está finalizando
        if (wasRecording && !isFinishing) {
            // Adiciona um extra à intenção para indicar que a atividade está pausando
            i.putExtra(ServicoMedicao.MAIN_ACTIVITY_PAUSE, true)
            startForegroundService(i) // Reinicia o serviço MeterService em primeiro plano
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume!")
        // Verifica se o serviço estava em execução
        if (isRunning) {
            Log.d(TAG, "onResume: restarta serviço (isRunning)")
            // Cria uma intenção para o serviço MeterService
            val i = Intent(applicationContext, ServicoMedicao::class.java)
            startForegroundService(i)  // Reinicia o serviço MeterService em primeiro plano
        }

        // redesenhar gráfico
        Graficos.ONE_SEC_LEFT.redesenhar()
        Graficos.ONE_SEC_RIGHT.redesenhar()
        Graficos.FIVE_MIN_LEFT.redesenhar()
        Graficos.FIVE_MIN_RIGHT.redesenhar()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
        ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalPermissionsApi::class
    )
    @Preview(name = "Vertical AppContent", showBackground = true)   // Se uma pré-visualização horizontal for desejada, então as pré-visualizações OneSecView e FiveMinView também devem ser horizontais (ou reescreva o código para lidar com esse caso).
    @Composable
    //Aqui é onde configuramos as permissões de áudio, rendezirar a interface principal e gerenciar o estado de execução
    fun AppContent(permissionState: PermissionState = FakePermissionState(PermissionStatus.Granted)) {
        // Calcula a classe de tamanho da janela, usada para ajustar a UI com base no tamanho da tela
        val windowSizeClass = if(!LocalInspectionMode.current) calculateWindowSizeClass(this) else WindowSizeClass.calculateFromSize(DpSize(360.dp, 760.dp))   // WindowSizeClass de fallback usado para a pré-visualização

        // Define as abas do aplicativo
        val tabs = listOf("Último segundo", "5 minutos anteriores")
        // Gerencia o estado do pager (controla qual aba está visível)
        val pagerState = rememberPagerState(initialPage = 0)
        // Cria um escopo de coroutine para operações assíncronas
        val coroutineScope = rememberCoroutineScope()
        // Gerencia o estado do botão play/pause
        var playOrPauseState by remember { mutableStateOf(if(isRunning) 1 else 0) }
        // Estado para mostrar ou não o diálogo de permissão

        // Coluna que organiza os componentes verticalmente
        val showPermissionDialog = remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxWidth()) {
            // Barra superior centralizada com o título do aplicativo
            CenterAlignedTopAppBar(title = { Text("Medidor de Decibéis", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    actions = { IconButton(onClick = {
                                        Log.d(TAG, "click, playOrPauseState: $playOrPauseState, showPermissionDialog: $showPermissionDialog")
                                        if (!permissionState.status.isGranted){
                                            // Solicita a permissão se não estiver concedida
                                            showPermissionDialog.value = true
                                            permissionState.launchPermissionRequest()
                                        } else {
                                            // Alterna entre iniciar e parar o serviço com base no estado play/pause
                                            if (playOrPauseState == 0){ // pausar
                                                val i = Intent(applicationContext, ServicoMedicao::class.java)
                                                startForegroundService(i)
                                                isRunning = true
                                            } else { // play
                                                val i = Intent(applicationContext, ServicoMedicao::class.java)
                                                stopService(i)
                                                isRunning = false
                                            }
                                            playOrPauseState = if (playOrPauseState==0) 1 else 0
                                        }
                                    }) {
                                        // Ícone de play/pause na barra superior
                                        Icon(
                                            imageVector = if (playOrPauseState==0) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                            contentDescription = "Gravação iniciada"
                                        )
                                        }
                                    }
            )

            // Barra de abas para navegação entre diferentes visões
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(text = { Text(title) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        // Ícones das abas, exibidos se a altura da tela não for compacta
                        icon = if (windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact){
                            { when (index) {
                                0 -> Icon(Icons.Default.Hearing, contentDescription = "Último segundo")
                                1 ->Icon(Icons.Default.History, contentDescription = "Últimos 5 minutos")
                                else -> Icon(Icons.Default.Star, contentDescription = "")   // não utilizado
                        }}} else null
                    )
                }
            }

            // Componente que permite deslizar entre diferentes visões
            HorizontalPager(
                pageCount = tabs.size,
                state = pagerState,
                beyondBoundsPageCount = 2
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> OneSecView() // Exibe a visão do "Último segundo"
                    1 -> FiveMinView() // Exibe a visão dos "5 minutos anteriores"
                }
            }

            // Verifica o estado da permissão e ajusta a User Interface(UI) e o comportamento do serviço
            if (!permissionState.status.isGranted){
                Log.d(TAG, "AppContent: Permissão negada!")
                playOrPauseState = 0
                isRunning = false
                val i = Intent(applicationContext, ServicoMedicao::class.java)
                stopService(i)
                showPermissionDialog.value = true
                // Mostra um diálogo se a permissão foi negada
                if (permissionState.status.shouldShowRationale) {
                    Log.d(TAG, "AppContent: shouldShowRationale")
                    NoPermissionDialog(openDialog = showPermissionDialog, shouldShowRationale = true)
                } else {
                    Log.d(TAG, "AppContent: NO shouldShowRationale")
                    NoPermissionDialog(openDialog = showPermissionDialog, shouldShowRationale = false)
                }
            } else {
                // Se é um cold start, o estado é ajustado para "play"
                if (coldStart)
                    playOrPauseState = 1
                Log.d(TAG, "AppContent: permission granted")
            }
            coldStart = false
        }

        // Executa uma ação colateral após a composição da UI
        SideEffect {
            Log.d(TAG, "AppContent: recomposing")
            if (!permissionState.status.isGranted){
                permissionState.launchPermissionRequest()
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Preview(name = "Vertical OneSecView", showBackground = true)
    @Composable
    //função ultimo segundo
    fun OneSecView() {
        // Estados para armazenar os níveis de decibéis e os valores de progresso
        var leftdb by rememberSaveable { mutableStateOf("Waiting left...") }
        var rightdb by rememberSaveable { mutableStateOf("Waiting right...") }

        var progressLeft by rememberSaveable { mutableStateOf(0.0f) }
        // Animação para o progresso do lado esquerdo
        val animatedProgressLeft by animateFloatAsState(
            targetValue = progressLeft,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
        )
        var progressRight by rememberSaveable { mutableStateOf(0.0f) }
        // Animação para o progresso do lado direito
        val animatedProgressRight by animateFloatAsState(
            targetValue = progressRight,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
        )

        var updateChartOneLeft by remember { mutableStateOf(0f) }
        var updateChartOneRight by remember { mutableStateOf(0f) }

        // Calcula o tamanho da janela para adaptar o layout
        val windowSizeClass = if(!LocalInspectionMode.current) calculateWindowSizeClass(this) else WindowSizeClass.calculateFromSize(DpSize(360.dp, 760.dp))  // Tamanho de fallback para pré-visualização
        // Verifica o tamanho da janela para decidir o arranjo dos elementos
        if (windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact) {
            Log.d(TAG, "In Column arrangement")
            // Arranjo em coluna para telas maiores
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {  // "contorno"
                LeftOneSecView(leftdb, animatedProgressLeft, updateChartOneLeft, modifier= Modifier.weight(1f))
                Spacer(modifier=Modifier.weight(0.1f)) // Espaço entre os componentes
                RightOneSecView(rightdb, animatedProgressRight, updateChartOneRight, modifier= Modifier.weight(1f))
            }
        } else {
            Log.d(TAG, "In Row arrangement")
            // Arranjo em linha para telas compactas
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {  // "contorno"
                LeftOneSecView(leftdb, animatedProgressLeft, updateChartOneLeft, modifier= Modifier.weight(1f))
                Spacer(modifier=Modifier.weight(0.1f)) // Espaço entre os componentes
                RightOneSecView(rightdb, animatedProgressRight, updateChartOneRight, modifier= Modifier.weight(1f))
            }
        }

        // Efeito colateral que atualiza os valores periodicamente
        LaunchedEffect(key1 = Unit) {
            var countToSec = 0  // Contador para verificar intervalos de 1 segundo
            while (true) {
                if (isRunning) {
                    countToSec++
                    if (countToSec >= 62) { // Aproximadamente 1 segundo (1000 ms / 16 ms por iteração)
                        countToSec = 0
                        // Obtém os valores mais recentes de decibéis para os dois canais
                        val dBLeftMax = Valores.lastSecDbLeftList.lastOrNull() ?: continue
                        val dBRightMax = Valores.lastSecDbRightList.lastOrNull() ?: continue
                        Log.d(TAG, "leftMax: $dBLeftMax, rightMax: $dBRightMax")
                        // Atualiza os valores de decibéis e progresso
                        leftdb = "%.${2}f".format(dBLeftMax)
                        progressLeft = dBToProgress(dBLeftMax)
                        rightdb = dBRightMax.toString()
                        rightdb = "%.${2}f".format(dBRightMax)
                        progressRight = dBToProgress(dBRightMax)
                    }

                    // Atualiza os gráficos com os últimos valores disponíveis
                    Log.d(TAG, "lastSecDbLeftList size: ${Valores.lastSecDbLeftList.size}, lastSecDbRightList size: ${Valores.lastSecDbRightList.size}")
                    Log.d(TAG, "lastLeft: ${Valores.lastLeft}, lastRight: ${Valores.lastRight}")
                    if (Valores.lastLeft != 0f) updateChartOneLeft = Valores.lastLeft
                    if (Valores.lastRight != 0f) updateChartOneRight = Valores.lastRight
                }

                delay(16)  // Aguarda aproximadamente 16 ms para manter a taxa de atualização em 60 Hz
            }
        }
    }

    @Composable
    fun LeftOneSecView(leftdb: String, progressLeft: Float, updateChartOneLeft: Float, modifier: Modifier = Modifier) {
        Row(modifier = modifier.padding(2.dp)) { // Contêiner horizontal com espaçamento de 2 dp
            Column(
                modifier = modifier.fillMaxWidth(), // Preenche toda a largura disponível
                horizontalAlignment = Alignment.CenterHorizontally // Alinha horizontalmente ao centro
            ) {  // Contêiner vertical para organizar os elementos
                Text(text = "Canal Esquerdo", fontWeight = FontWeight.Bold) // Título em negrito
                // Linha para organizar o texto e o indicador de progresso horizontalmente
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = modifier.fillMaxWidth()) { // Alinha verticalmente ao centro
                    Text(text = "Medindo... dB", modifier= Modifier // Texto com espaçamento à esquerda e peso para ocupar a largura disponível
                        .padding(9.dp, 0.dp, 0.dp, 0.dp) // Espaçamento à esquerda
                        .weight(1f)) // Ocupa uma fração da largura disponível
                    // Indicador de progresso linear
                    LinearProgressIndicator(
                        modifier = modifier
                            .semantics(mergeDescendants = true) {} // Define semântica para acessibilidade
                            .requiredHeight(PROGRESS_BAR_HEIGHT) // Define a altura do indicador de progresso
                            .requiredWidth(PROGRESS_BAR_WIDTH) // Define a largura do indicador de progresso
                            .weight(2f) // Ocupa o dobro da largura disponível em comparação com o texto
                            .padding(0.dp, 0.dp, 17.dp, 0.dp), // Espaçamento à direita
                        progress = progressLeft, // Valor do progresso
                    )
                }

                // Gráfico de um segundo com base no valor de atualização
                Graficos.ONE_SEC_LEFT(updateTrigger = updateChartOneLeft, modifier = modifier.fillMaxSize())
            }
        }
    }

    @Composable
    fun RightOneSecView(rightdb: String, progressRight: Float, updateChartOneRight: Float, modifier: Modifier = Modifier){
        Row(modifier = modifier.padding(2.dp)) { // Contêiner horizontal com espaçamento de 2 dp
            Column(
                modifier = modifier.fillMaxWidth(), // Preenche toda a largura disponível
                horizontalAlignment = Alignment.CenterHorizontally // Alinha horizontalmente ao centro
            ) {  // Contêiner vertical para organizar os elementos
                Text(text = "Canal Direito", fontWeight = FontWeight.Bold) // Título em negrito
                // Linha para organizar o texto e o indicador de progresso horizontalmente
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround, modifier = modifier.fillMaxWidth()) {
                    // Texto com espaçamento à esquerda e peso para ocupar a largura disponível
                    Text(text = "Medindo... dB", modifier= Modifier
                        .padding(9.dp, 0.dp, 0.dp, 0.dp) // Ocupa uma fração da largura disponíve
                        .weight(1f)) // Espaçamento à esquerda
                    // Indicador de progresso linear
                    LinearProgressIndicator(
                        modifier = modifier
                            .semantics(mergeDescendants = true) {}  // Define semântica para acessibilidade
                            .requiredHeight(PROGRESS_BAR_HEIGHT) // Define a altura do indicador de progresso
                            .requiredWidth(PROGRESS_BAR_WIDTH) // Define a largura do indicador de progress
                            .weight(1.5f) // Ocupa 1,5 vezes mais a largura disponível em comparação com o texto
                            .padding(0.dp, 0.dp, 17.dp, 0.dp), // Espaçamento à direita
                        progress = progressRight, // Valor do progresso
                    )
                }

                // Gráfico de um segundo com base no valor de atualização
                Graficos.ONE_SEC_RIGHT(updateTrigger = updateChartOneRight, modifier = modifier.fillMaxSize())
            }
        }
    }


    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Preview(name = "Vertical FiveMinView", showBackground = true)
    @Composable
    fun FiveMinView(){
        // Estado para armazenar os valores de atualização dos gráficos de 5 minutos
        var onUpdateChartFiveLeft by remember { mutableStateOf(0f) }
        var onUpdateChartFiveRight by remember { mutableStateOf(0f) }

        // Obtém a classe de tamanho da janela, com um fallback para pré-visualização
        val windowSizeClass = if(!LocalInspectionMode.current) calculateWindowSizeClass(this) else WindowSizeClass.calculateFromSize(DpSize(360.dp, 760.dp))   // Fallback para pré-visualização
        // Layout condicional com base na altura da janela
        if (windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact) {
            Log.d(TAG, "In Column arrangement")
            Column(
                modifier = Modifier
                    .fillMaxSize() // Preenche toda a área disponível
                    .padding(12.dp), // Adiciona um padding de 12 dp
                verticalArrangement = Arrangement.SpaceEvenly // Distribui o espaço verticalmente de forma uniforme
            ) {
                // Exibe a visualização para o canal esquerdo de 5 minutos
                LeftFiveMinView(onUpdateChartFiveLeft, modifier= Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(0.05f)) // Espaço entre as visualizações
                // Exibe a visualização para o canal direito de 5 minutos
                RightFiveMinView(onUpdateChartFiveRight, modifier= Modifier.weight(1f))
            }
        } else {
            Log.d(TAG, "In Row arrangement")
            Row(
                modifier = Modifier
                    .fillMaxSize() // Preenche toda a área disponíve
                    .padding(12.dp), // Adiciona um padding de 12 dp
                horizontalArrangement = Arrangement.SpaceEvenly // Adiciona um padding de 12 dp
            ) {
                // Exibe a visualização para o canal esquerdo de 5 minutos
                LeftFiveMinView(onUpdateChartFiveLeft, modifier= Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(0.05f)) // Espaço entre as visualizações
                // Exibe a visualização para o canal direito de 5 minutos
                RightFiveMinView(onUpdateChartFiveRight, modifier= Modifier.weight(1f))
            }
        }

        // Efeito colateral para atualizar os gráficos a cada segundo
        LaunchedEffect(key1 = Unit, block = {
            while (true){
                if(isRunning){
                    Log.d(TAG, "Launched effect: FiveMinView")

                    // Atualiza os valores dos gráficos com valores aleatórios para acionar a recomposição
                    onUpdateChartFiveLeft = (0..1_000_000).random().toFloat()
                    onUpdateChartFiveRight = (0..1_000_000).random().toFloat()
                }


                delay(1000) // Espera 1 segundo antes de repetir o loop
            }
        })

    }


    @Composable
    fun LeftFiveMinView(updateChartFiveLeft: Float, modifier: Modifier = Modifier){
        // Contêiner horizontal com padding de 2 dp ao redor
        Row(modifier = modifier.padding(2.dp)) { // esquerda
            // Contêiner vertical que preenche a largura disponível e alinha os elementos ao centro horizontalmente
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {  // arranjo em coluna
                // Texto indicando o canal esquerdo com fonte em negrito
                Text(text = "Canal Esquerdo", fontWeight = FontWeight.Bold)
                // Gráfico de 5 minutos para o canal esquerdo
                Graficos.FIVE_MIN_LEFT(updateTrigger = updateChartFiveLeft, modifier = Modifier
                    .fillMaxSize() // Preenche toda a área disponível
                    .padding(2.dp)) // Adiciona padding de 2 dp ao redor do gráfico
            }
        }
    }

    @Composable
    fun RightFiveMinView(updateChartFiveRight: Float, modifier: Modifier = Modifier){
        // Contêiner horizontal com padding de 2 dp ao redor
        Row(modifier = modifier.padding(2.dp)) {
            // Contêiner vertical que preenche a largura disponível e alinha os elementos ao centro horizontalmente
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Texto indicando o canal direito com fonte em negrito
                Text(text = "Canal Direito", fontWeight = FontWeight.Bold)
                // Gráfico de 5 minutos para o canal direito
                Graficos.FIVE_MIN_RIGHT(updateTrigger = updateChartFiveRight, modifier = Modifier
                    .fillMaxSize() // Preenche toda a área disponível
                    .padding(2.dp)) // Adiciona padding de 2 dp ao redor do gráfico
            }
        }
    }


    @Preview
    @Composable
    fun NoPermissionDialog(openDialog: MutableState<Boolean> = mutableStateOf(true), shouldShowRationale: Boolean = true) {
        Log.d(TAG, "NoPermissionDialog, onShowPermissionDialog: $openDialog")

        // Verifica se o diálogo deve ser exibido
        if (!openDialog.value) return
        // Exibe um AlertDialog
        AlertDialog(
            onDismissRequest = { openDialog.value = false }, // Fecha o diálogo ao clicar fora dele
            icon = { Icon(Icons.Filled.Mic, contentDescription = null) }, // Ícone do microfone
            title = { Text(text = "Permissão não concedida") }, // Título do diálogo
            text = { Text(text = "Por favor, permita que o aplicativo grave o áudio deste dispostivo.") }, // Texto explicativo
            // Botão de desfazer (dispensável) exibido apenas se não for necessário mostrar uma explicação adicional
            dismissButton = if(!shouldShowRationale) {{
                    Button(onClick = { openDialog.value = false }) {
                        Text(text = "Mais tarde!")
                    }
                }} else null,
            // Botão de confirmação
            confirmButton = if(!shouldShowRationale) {
                {
                    Button(onClick = {
                        // Cria um Intent para abrir as configurações do aplicativo
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        intent.putExtra(":settings:show_fragment_args", bundleOf(":settings:fragment_args_key" to "permission_settings"))   // Destaca a linha de permissão
                        startActivity(intent)
                    }) {
                        Text(text = "Ir para as configurações")
                    }
                }} else {
                {
                    Button(onClick = { openDialog.value = false }) {
                        Text(text = "OK")
                    }

                }}
        )
    }

    @ExperimentalPermissionsApi
    private class FakePermissionState( // Classe privada usada para simular o estado de permissão em pré-visualizações
        override val status: PermissionStatus, // Estado da permissão simulado
        override val permission: String = "Not used, this is fake!" // Nome da permissão, não usado aqui
    ): PermissionState { // Implementa a interface PermissionState
        // Implementação do método da interface PermissionState que lança uma solicitação de permissão
        // Aqui, o método lança uma exceção, pois não é implementado
        override fun launchPermissionRequest(): Unit = throw NotImplementedError()
    }

}



