/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redes;

/**
 * @author flavio
 */

 import java.io.FileInputStream;
 import java.io.IOException;
 import java.net.DatagramPacket;
 import java.net.DatagramSocket;
 import java.net.InetAddress;
 import java.net.SocketException;
 import java.nio.ByteBuffer;
 import java.nio.IntBuffer;
 import java.util.logging.Level;
 import java.util.logging.Logger;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.concurrent.Semaphore;
 import java.util.Timer;
 import java.util.TimerTask;


public class EnviaDados extends Thread {

    private final int portaLocalEnvio = 2000;
    private final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    Semaphore sem;
    private final String funcao;
    private final ConcurrentHashMap<Integer, int[]> pacotesEnviados = new ConcurrentHashMap<>(); // Armazena os pacotes enviados
    private int sendBase;
    private int N; // tamanho da janela
    private boolean timerRunning;
    private Timer timer;
    private long timeoutInterval; // intervalo de timeout em milissegundos
    private boolean timeoutOccurred; 
    private int numeroDeSequencia;
    private int[] dados = new int[350]; // Array de inteiros longos

    public EnviaDados(Semaphore sem, String funcao) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
        this.timeoutInterval = 2000;
    }

    public String getFuncao() {
        return funcao;
    }

    private byte[] converteParaBytes(int[] dados) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(dados.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(dados);
        return byteBuffer.array();
    }

    private void enviaPct(int numeroSequencia, int[] dados) {
        
        byte[] buffer = converteParaBytes(dados);

        try {
            System.out.println("Semaforo: " + sem.availablePermits());
            sem.acquire();
            System.out.println("Semaforo: " + sem.availablePermits());

            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, portaDestino);
                datagramSocket.send(packet);
            }

            System.out.println("Envio feito.");
        } catch (SocketException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            sem.release();
        }
    }

    private synchronized void retransmitePct(int numeroSequencia, int[] dados) {
        
        byte[] buffer = converteParaBytes(dados);

        if (buffer == null) {
            System.out.println("Pacote número " + numeroSequencia + " não encontrado");
            return;
        }

        try {
            System.out.println("Semaforo: " + sem.availablePermits());
            sem.acquire();
            System.out.println("Semaforo: " + sem.availablePermits());
    
            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, portaDestino);
                datagramSocket.send(packet);
            }
    
            System.out.println("Retransmissão feita do pacote " + numeroSequencia);
        } catch (SocketException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
            Thread.currentThread().interrupt(); 
        } finally {
            sem.release();
        }
    }


    private void startTimer() {
        stopTimer(); // para qualquer temporizador existente
        timerRunning = true;
        timeoutOccurred = false;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeoutOccurred = true;
                System.out.println("Timeout ocorreu");
                retransmitirPacotesPendentes();
            }
        }, timeoutInterval);
        System.out.println("Timer iniciado");
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        timerRunning = false;
        System.out.println("Timer parado");
    }

    private void retransmitirPacotesPendentes() {
        boolean pacotesRetransmitidos = false;

        for (int i = sendBase; i < numeroDeSequencia; i++) {
            int[] dadosPerdidos = pacotesEnviados.get(i);
            if (dadosPerdidos != null) {
                retransmitePct(i, dadosPerdidos);
                pacotesRetransmitidos = true;
            } else {
                System.out.println("Pacote número " + i + " não encontrado");
            }
        }

        if (pacotesRetransmitidos) {
            startTimer(); // Reinicia o temporizador após retransmitir pacotes
        }
    }

    @Override
    public void run() {
        switch (this.getFuncao()) {
            case "envia":
                //variavel onde os dados lidos serao gravados
                this.dados = new int[350];
                //contador, para gerar pacotes com 1400 Bytes de tamanho
                //como cada int ocupa 4 Bytes, estamos lendo blocos com 350
                //int's por vez.
                int cont = 0;
                //numero de sequencia para sabermos a ordem dos pacotes
                numeroDeSequencia = 0; 
                this.N = 4;
                this.sendBase = 0;
                this.timerRunning = false;
            
                

                try (FileInputStream fileInput = new FileInputStream("entrada");) {
                    int lido;
                    while ((lido = fileInput.read()) != -1) {
                        //dados[0] = numero de sequencia enviado
                        
                        dados[cont+1] = lido;

                        //define o número de sequência no início do pkt
                        if (cont == 1){
                            dados[0] = numeroDeSequencia;
                        }
                        
                        cont++;
                        if (cont == 349) {
                            //envia pacotes a cada 350 int's lidos.
                            //ou seja, 1400 Bytes.
                            //colocar numero de sequencia, cada pkt tem um numero de sequencia
                            //os 4 primeiros bytes vao ser o numero de sequencia
                            //ao inves de 350 a gente vai ter 349 de dados
                            if (numeroDeSequencia < sendBase + N) {
                                pacotesEnviados.put(numeroDeSequencia, dados.clone()); //colocando dados no map para retransmissao
                                System.out.println("Enviando pacote " + numeroDeSequencia);
                                enviaPct(numeroDeSequencia, dados);
                                if (sendBase == numeroDeSequencia) {
                                    startTimer();
                                }
                                numeroDeSequencia++;
                                cont = 0;
                                dados = new int[350];
                            }

                            
                        }
                    }

                    //ultimo pacote eh preenchido com
                    //-1 ate o fim, indicando que acabou
                    //o envio dos dados.
                    for (int i = cont+1; i < 350; i++)
                        dados[i] = -1;

                    pacotesEnviados.put(numeroDeSequencia, dados.clone());  // Armazena o último pacote para possível retransmissão
                    enviaPct(numeroDeSequencia, dados);
                } catch (IOException e) {
                    System.out.println("Error message: " + e.getMessage());
                }
                break;
            case "ack":
                try {
                    
                    DatagramSocket serverSocket = new DatagramSocket(portaLocalRecebimento);
                    // mudando o tamanho do buffer para pegar a letra equivalente e o numero de sequencia
                    byte[] receiveData = new byte[5]; 
                    String retorno = "";
                    int ackTotal = -1;
                    while (true) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        retorno = new String(receivePacket.getData(), 0, receivePacket.getLength());
                       

                        if (retorno.startsWith("A")) {
                            int ackNum = Integer.parseInt(retorno.substring(1).trim());
                            ackTotal = ackTotal +1;
                            System.out.println("ACK " + ackTotal + " recebido.");
                            if (ackNum >= sendBase) {
                                sendBase = ackNum + 1;
                                if (sendBase == numeroDeSequencia) {
                                    stopTimer();
                                } else {
                                    startTimer();
                                }
                            }
                            sem.release();
                        } else if (retorno.startsWith("R")) { //se for solicitada uma retransmissao vai vir como R ai pede a retransmissao do pacote
                            int numeroSequencia = Integer.parseInt(retorno.substring(1).trim());
                            System.out.println("Retransmissão solicitada para pacote " + numeroSequencia);
                            
                            //boolean contemChave = pacotesEnviados.containsKey(numeroSequencia);
                            //System.out.println("Sendbase: " + sendBase + " Numero de sequencia: " + numeroSequencia);
                            //int [] dadosPerdidos = pacotesEnviados.get(numeroSequencia);
                            //enviaPct(numeroSequencia, dadosPerdidos);
                            //retransmitePct(numeroSequencia, dadosPerdidos);
                            //sendBase = numeroSequencia - 1;
                            if (numeroSequencia < numeroDeSequencia) {
                                int[] dadosPerdidos = pacotesEnviados.get(numeroSequencia);
                                if (dadosPerdidos != null) {
                                    retransmitirPacotesPendentes();
                                    startTimer();
                                } else {
                                    System.out.println("Pacote número " + numeroSequencia + " não encontrado");
                                }
                            }
                        } else if (retorno.equals("F")) {
                            System.out.println("Fim da transmissão.");
                            break;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Excecao: " + e.getMessage());
                }
                break;
            

            default:
                break;
        }

    }
}
