package gustavo.medidordcb

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import gustavo.medidordcb.R
import java.util.Timer
import java.util.TimerTask


class ServicoMedicao : Service() {
    // Declaração de variáveis para o WakeLock (evitar que a CPU do dispositivo durma) e Timer (gerenciamento de tempo)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var timer: Timer

    // Declaração de variáveis para as threads de gravação e leitura de áudio
    private lateinit var recordThread: AudioRecordThread
    private lateinit var readThread: AudioReadThread

    // Este método é chamado quando o serviço é criado
    @SuppressLint("MissingPermission")  // Suprime o aviso de falta de permissão, pois a permissão é solicitada na MainActivity
    override fun onCreate() {
        super.onCreate()
        // Inicializa o objeto AudioRecord para capturar áudio com as configurações especificadas
        meter = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)

        // Cria um NotificationChannel para notificações do serviço, mas apenas em API nível 26 ou superior
        // O NotificationChannel é necessário para mostrar notificações de forma consistente em versões mais recentes do Android
        // Veja mais detalhes em: https://developer.android.com/training/notify-user/channels
        val channel = NotificationChannel(CHANNEL_ID, "SoundMeterESP", NotificationManager.IMPORTANCE_LOW)
        channel.description = "SoundMeterESP"
        // Registra o canal de notificação com o sistema para que as notificações possam ser exibidas corretamente
        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)
    }

    // Este método é chamado quando um cliente tenta se vincular ao serviço, mas este serviço não permite vinculação
    override fun onBind(intent: Intent): IBinder? {
        return null  // Os clientes não podem se vincular a este serviço
    }

    @SuppressLint("WakelockTimeout")    // Suprime o aviso de falta de tempo limite para o WakeLock, pois é tratado em MainActivity.onPause
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Verifica se o serviço já está em execução. Se estiver, reaquece o WakeLock e cancela o timer (se estiver inicializado)
        if (isRecording){
            Log.d(TAG, "Service already running")
            wakeLock.acquire() // Reaquece o WakeLock sem tempo limite, já que a MainActivity está na tela
            if (this::timer.isInitialized) timer.cancel()
            return START_NOT_STICKY // O serviço não será reiniciado automaticamente se for encerrado pelo sistema
        }

        // Construir uma notificação que será exibida ao usuário enquanto o serviço estiver em execução
        // Em API nível 33 ou superior, se a permissão para notificações não for concedida, ela não será solicitada até a próxima instalação do app
        val notificationBuilder: Notification.Builder =
            Notification.Builder(applicationContext, CHANNEL_ID)
        notificationBuilder.setContentTitle("SoundMeterESP") // Título da notificação
        notificationBuilder.setContentText("Recording sounds...") // Texto da notificação
        notificationBuilder.setSmallIcon(R.drawable.ic_stat_name) // Ícone da notificação
        // Intenção para voltar à MainActivity ao tocar na notificação
        val goToMainActivityIntent = Intent(applicationContext, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP // Usa a instância existente da MainActivity no topo da pilha
        }
        // Cria um PendingIntent para abrir a MainActivity quando o usuário tocar na notificação
        val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, goToMainActivityIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBuilder.setContentIntent(pendingIntent) // Define o comportamento da notificação ao ser clicada
        // Constrói a notificação
        val notification = notificationBuilder.build() // Requer nível de API 16
        // Executa este serviço em primeiro plano, o que significa que ele será menos propenso a ser interrompido pelo sistema
        // A notificação será exibida ao usuário enquanto o serviço estiver em execução
        val notificationID = 2000162 // ID único para esta notificação dentro do aplicativo
        startForeground(notificationID, notification) // Inicia o serviço em primeiro plano
        Log.d(TAG, "rec: startForeground")

        // Inicializa o WakeLock para manter o dispositivo acordado enquanto o serviço estiver em execução
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundMeterESP:$TAG").apply {
                setReferenceCounted(false) // Permite adquirir e liberar o WakeLock múltiplas vezes sem liberar automaticamente após a primeira chamada
                if (intent.getBooleanExtra(MAIN_ACTIVITY_PAUSE, false))
                    acquire(10*60*1000L /*10 minutos*/) // Mantém o dispositivo acordado por 10 minutos se a MainActivity estiver pausada
                else
                    acquire()   // Mantém o dispositivo acordado indefinidamente enquanto o serviço estiver em execução
            }
        }
        // Se a MainActivity estiver pausada, inicia um timer para encerrar o serviço após 10 minutos
        if (intent.getBooleanExtra(MAIN_ACTIVITY_PAUSE, false)) {
            timer = Timer(true)  // Cria um novo Timer em modo de daemon (paralelo)
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    Log.d(TAG, "TimerTask: stopSelf") // Registra que o serviço será encerrado
                    stopSelf() // Encerra o serviço
                    MainActivity.coldStart = true // Marca o cold start da MainActivity para a próxima vez que for aberta
                    timer.cancel() // Cancela o timer
                }
            }
            timer.schedule(timerTask, 600000)    // Agenda o timer para executar o TimerTask após 10 minutos (600000 milissegundos)
        }


        Log.d(TAG, "Start recording thread")
        recordThread = AudioRecordThread()
        recordThread.start()

        Log.d(TAG, "Start reading thread")
        readThread = AudioReadThread()
        readThread.start()

        // Retorna o valor indicando que o sistema não deve reiniciar o serviço automaticamente
        return START_NOT_STICKY
    }

    override fun onDestroy()
    {
        Log.d(TAG, "onDestroy!") // Registra no log que o método onDestroy() foi chamado
        // Para a thread de leitura de áudio
        readThread.stopReading()
        // Para a thread de gravação de áudio
        recordThread.stopRecording()

        // Verifica se o objeto AudioRecord foi inicializado. Se sim, o libera para liberar recursos de áudio.
        if (meter?.state == AudioRecord.STATE_INITIALIZED)
            meter?.release() ?: Log.d(TAG, "meter was not initialized") // Libera o objeto AudioRecord se ele foi inicializado
        meter = null // Define o objeto AudioRecord como null, indicando que ele foi liberado

        // Interrompe a thread de gravação
        recordThread.interrupt()

        // Para o serviço em primeiro plano e remove a notificação associada
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Cancela o Timer se ele estiver inicializado
        if (this::timer.isInitialized) timer.cancel()
        // Libera o WakeLock para permitir que o dispositivo entre em modo de suspensão
        wakeLock.release()

        // Chama o método onDestroy() da classe pai para realizar a limpeza adicional necessária
        super.onDestroy()
    }


    private inner class AudioRecordThread : Thread("AudioRecordThread") {
        init {
            isDaemon = true  // Define a thread como um daemon, o que significa que ela será encerrada automaticamente quando o aplicativo for fechado
        }

        override fun run() {
            super.run()
            // Verifica se o objeto AudioRecord (meter) está nulo e registra isso no log, se necessário
            if (meter == null) Log.d(TAG, "rec: meter is null")
            Log.d(TAG, "Starting AudioRecordThread") // Registra que a thread de gravação de áudio foi iniciada
            isRecording = true // Define a flag isRecording como true para indicar que a gravação está em andamento
            meter?.startRecording() // Inicia a gravação de áudio usando o objeto AudioRecord (meter)

            try{
                sleep(500) // Faz a thread dormir por 500 milissegundos, possivelmente para dar tempo de inicialização
            } catch (e: InterruptedException) {
                currentThread().interrupt() // Interrompe a thread se ocorrer uma InterruptedException
            }
            // Inicia o loop de gravação, que continua enquanto isRecording for true
            while (isRecording)
                if (meter?.recordingState == AudioRecord.RECORDSTATE_RECORDING) readLeftRightMeter(meter!!) // Lê e processa os dados de áudio enquanto o estado de gravação for RECORDSTATE_RECORDING

            // Libera os recursos do AudioRecord aqui, após o loop de gravação terminar
            Log.d(TAG, "state: "+meter?.state.toString()) // Registra o estado atual do AudioRecord
            Log.d(TAG, "recordingState: "+meter?.recordingState.toString()) // Registra o estado de gravação do AudioRecord
            if (meter?.recordingState == AudioRecord.RECORDSTATE_RECORDING) meter?.stop() // Para a gravação se o estado ainda for de gravação (RECORDSTATE_RECORDING)
        }

        fun stopRecording() {
            // Define isRecording como false para parar o loop de gravação na próxima iteração
            isRecording = false
        }
    }

    private inner class AudioReadThread : Thread("AudioReadThread") {
        init {
            isDaemon = true // Define a thread como um daemon, ou seja, ela será encerrada automaticamente quando o aplicativo for fechado
        }
        var isReading = true // Flag para controlar se a thread deve continuar lendo dados

        override fun run() {
            super.run()
            Log.d(TAG, "Starting AudioReadThread") // Registra no log que a thread de leitura de áudio foi iniciada
            try{
                sleep(1000) // Faz a thread dormir por 1 segundo antes de iniciar o loop de leitura
            } catch (e: InterruptedException) {
                currentThread().interrupt()  // Interrompe a thread se ocorrer uma InterruptedException
            }

            var countToSec = 0  // Variável para contar até aproximadamente 1 segundo (62 ciclos de 16 ms)
            while(isReading){ // Loop de leitura continua enquanto isReading for true
                countToSec++
                try {
                    if (countToSec >= 62) { // Aproximadamente 1 segundo se passou
                        countToSec = 0 // Reseta o contador
                        Valores.getMaxDbLastSec() // Calcula e possivelmente salva o valor máximo de decibéis do último segundo
                        // Log.d(TAG, "Dados do último segundo salvos")
                    }

                    // Obtém e processa os primeiros valores das filas de áudio para o canal esquerdo e direito
                    Valores.getFirstFromQueueLeft()
                    Valores.getFirstFromQueueRight()
                // Log.d(TAG, "Dados em tempo real salvos")
                } catch (e: ConcurrentModificationException) {
                    Log.d(TAG, "ConcurrentModificationException")   // Loga a exceção, que ocorre se as filas estiverem vazias, mas não é um problema grave
                }
                sleep(16)   // Loga a exceção, que ocorre se as filas estiverem vazias, mas não é um problema grave
            }
        }

        fun stopReading() {
            // Define isReading como false para parar o loop de leitura
            isReading = false
        }
    }


    private fun readLeftRightMeter(meter: AudioRecord) {
        val buf = ShortArray(BUFFER_SIZE) // Cria um buffer de shorts com o tamanho definido por BUFFER_SIZE
        var readN = 0 // Inicializa o número de amostras lidas como 0

        try{
            readN += meter.read(buf, 0, BUFFER_SIZE) // Lê dados de áudio do objeto AudioRecord (meter) para o buffer e incrementa readN com o número de amostras lidas
            if (readN == 0) Log.d(TAG, "readN=0") // Se nenhuma amostra foi lida, registra isso no log
        }catch (e: Exception){
            Log.d(TAG, e.toString())  // Se ocorrer uma exceção durante a leitura, registra a exceção no log
            return  // Sai da função se uma exceção ocorrer
        }
        // Processa os dados lidos para separar os canais esquerdo e direito
        val left = buf.slice(0 until readN step 2).map { Valores.pcmToDb(it) }.toFloatArray() // Extrai as amostras do canal esquerdo e converte para decibéis
        val right = buf.slice(1 until readN step 2).map { Valores.pcmToDb(it) }.toFloatArray() // Extrai as amostras do canal direito e converte para decibéis
        Log.d(TAG, "readLeftRightMeter: left: ${left.size} right: ${right.size}") // Registra o tamanho dos arrays processados no log
        Valores.updateQueues(left, right, readN)  // Atualiza as filas de valores de áudio com os dados processados
    }


    companion object
    {
        private var meter: AudioRecord? = null // Variável estática que mantém a referência ao objeto AudioRecord

        private const val CHANNEL_ID = "soundmeteresp" // ID do canal de notificação para o serviço
        const val MAIN_ACTIVITY_PAUSE = "MainActivityPause" // String constante usada como chave para intent extras

        private var isRecording = false // Flag para indicar se o serviço está gravando áudio

        private val TAG = ServicoMedicao::class.simpleName // TAG para logs, usando o nome da classe

        const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC  // Fonte de áudio é o microfone. O valor UNPROCESSED não é suportado em todos os dispositivos, então MIC é usado
        const val SAMPLE_RATE = 44100   // Taxa de amostragem de 44,1 kHz, que é suportada pela maioria dos dispositivos
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO // Configuração do canal de áudio como estéreo
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // Formato do áudio em PCM de 16 bits
        val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) // Calcula o tamanho mínimo do buffer de áudio. Como o método retorna o tamanho em bytes, multiplicação por 2 pode ser necessária para lidar com shorts
    }




}

