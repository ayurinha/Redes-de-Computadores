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


public class EnviaDados extends Thread {

    private final int portaLocalEnvio = 2000;
    private final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    Semaphore sem;
    private final String funcao;
    private final ConcurrentHashMap<Integer, byte[]> pacotesEnviados = new ConcurrentHashMap<>(); // Armazena os pacotes enviados

    public EnviaDados(Semaphore sem, String funcao) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
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
        pacotesEnviados.put(numeroSequencia, buffer);  // Armazena o pacote para possível retransmissão

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
        }
    }

    private void retransmitePct(int numeroSequencia) {
        byte[] buffer = pacotesEnviados.get(numeroSequencia);

        if (buffer == null) {
            System.out.println("Pacote número " + numeroSequencia + " não encontrado para retransmissão.");
            return;
        }

        try {
            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, portaDestino);
                datagramSocket.send(packet);
            }

            System.out.println("Retransmissão feita. Pacote número: " + numeroSequencia);
        } catch (SocketException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        switch (this.getFuncao()) {
            case "envia":
                //variavel onde os dados lidos serao gravados
                int[] dados = new int[350];
                //contador, para gerar pacotes com 1400 Bytes de tamanho
                //como cada int ocupa 4 Bytes, estamos lendo blocos com 350
                //int's por vez.
                int cont = 1;
                //numero de sequencia para sabermos a ordem dos pacotes
                int numeroDeSequencia = 0; 

                try (FileInputStream fileInput = new FileInputStream("entrada");) {
                    int lido;
                    while ((lido = fileInput.read()) != -1) {
                        //dados[0] = numero de sequencia enviado
                        //ordem = incrementa a cada pkt enviado
                        dados[cont] = lido;

                        //define o número de sequência no início do pkt
                        if (cont == 1){
                            dados[0] = numeroDeSequencia;
                        }
                        
                        cont++;
                        if (cont == 350) {
                            //envia pacotes a cada 350 int's lidos.
                            //ou seja, 1400 Bytes.
                            //colocar numero de sequencia, cada pkt tem um numero de sequencia
                            //os 4 primeiros bytes vao ser o numero de sequencia
                            //ao inves de 350 a gente vai ter 349 de dados
                            System.out.println("Enviado pkt numero " + numeroDeSequencia);
                            enviaPct(numeroDeSequencia, dados);
                            numeroDeSequencia++;
                            cont = 1;
                        }
                    }

                    //ultimo pacote eh preenchido com
                    //-1 ate o fim, indicando que acabou
                    //o envio dos dados.
                    for (int i = cont; i < 350; i++)
                        dados[i] = -1;
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
                    while (true) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        retorno = new String(receivePacket.getData(), 0, receivePacket.getLength());

                        if (retorno.startsWith("A")) {
                            System.out.println("ACK recebido " + retorno + ".");
                            sem.release();
                        } else if (retorno.startsWith("R")) { //se for solicitada uma retransmissao vai vir como R ai pede a retransmissao do pacote
                            int numeroSequencia = Integer.parseInt(retorno.substring(1).trim());
                            System.out.println("Retransmissão solicitada para o pacote " + numeroSequencia);
                            retransmitePct(numeroSequencia);
                        } else if (retorno.equals("F")) {
                            System.out.println("Fim da transmissão.");
                            break;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Excecao: " + e.getMessage());
                }
                break;
            //TODO timer
            case "timout":
            //escrever caso de timout
            //se relogio > 0.20 chama a envia de novo 
            //thread.sleep pesquisar
            //chama enviapkt com dados
            break;

            default:
                break;
        }

    }
}
