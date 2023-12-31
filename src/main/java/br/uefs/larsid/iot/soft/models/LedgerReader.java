package br.uefs.larsid.iot.soft.models;

import br.uefs.larsid.iot.soft.models.tangle.Payload;
import br.uefs.larsid.iot.soft.models.transactions.Evaluation;
import br.uefs.larsid.iot.soft.models.transactions.Transaction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Allan Capistrano
 */
public class LedgerReader implements Runnable {

  /*-------------------------Constantes---------------------------------------*/
  private static final long SLEEP = 5000;
  private static final String ENDPOINT = "message";
  private static final String ENDPOINT_MESSAGE_ID = "message/messageId";
  /*--------------------------------------------------------------------------*/

  private Thread ledgerReader;
  private final String index;
  private boolean debugModeValue;
  private String urlApi;

  private static final Logger logger = Logger.getLogger(
    LedgerReader.class.getName()
  );

  public LedgerReader(
    String protocol,
    String url,
    int port,
    String index,
    boolean debugModeValue
  ) {
    this.urlApi = String.format("%s://%s:%s", protocol, url, port);
    this.debugModeValue = debugModeValue;
    this.index = index;

    if (this.ledgerReader == null) {
      this.ledgerReader = new Thread(this);
      this.ledgerReader.setName("TANGLE_MONITOR/LEDGER_READER");
      this.ledgerReader.start();
    }
  }

  /**
   * Obtêm todas as transações a partir de um índice específico.
   *
   * @param index String - Índice das transações.
   * @return List<Transaction>
   */
  public List<Transaction> getTransactionsByIndex(String index) {
    String response = null;
    List<Transaction> transactions = new ArrayList<Transaction>();

    try {
      URL url = new URL(
        String.format("%s/%s/%s", this.urlApi, ENDPOINT, index)
      );
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();

      if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new RuntimeException(
          "HTTP error code : " + conn.getResponseCode()
        );
      }

      BufferedReader br = new BufferedReader(
        new InputStreamReader((conn.getInputStream()))
      );

      String temp = null;

      while ((temp = br.readLine()) != null) {
        response = temp;
      }

      conn.disconnect();

      transactions =
        Transaction.jsonArrayInStringToTransaction(response, debugModeValue);

      return transactions;
    } catch (MalformedURLException mue) {
      if (debugModeValue) {
        logger.severe(mue.getMessage());
      }
    } catch (IOException ioe) {
      if (debugModeValue) {
        logger.severe(ioe.getMessage());
      }
    }

    return transactions;
  }

  /**
   * Obtém uma transação a partir de um ID.
   *
   * @param transactionId String - ID da transação.
   * @return Transaction
   */
  public Transaction getTransactionById(String transactionId) {
    String response = null;

    try {
      URL url = new URL(
        String.format(
          "%s/%s/%s/",
          this.urlApi,
          ENDPOINT_MESSAGE_ID,
          transactionId
        )
      );
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();

      if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new RuntimeException(
          "HTTP error code : " + conn.getResponseCode()
        );
      }

      BufferedReader br = new BufferedReader(
        new InputStreamReader((conn.getInputStream()))
      );

      String temp = null;

      while ((temp = br.readLine()) != null) {
        response = temp;
      }

      conn.disconnect();

      return Transaction.getTransactionObjectByType(
        Payload.stringToPayload(response).getData(),
        debugModeValue
      );
    } catch (MalformedURLException mue) {
      if (debugModeValue) {
        logger.severe(mue.getMessage());
      }
    } catch (IOException ioe) {
      if (debugModeValue) {
        logger.severe(ioe.getMessage());
      }
    }

    return null;
  }

  /**
   * Thread para obter a(s) transação/transações.
   */
  @Override
  public void run() {
    while (!this.ledgerReader.isInterrupted()) {
      try {
        long start = System.currentTimeMillis();

        List<Transaction> transactions =
          this.getTransactionsByIndex(this.index);

        transactions.sort((t1, t2) ->
          Long.compare(t1.getCreatedAt(), t2.getCreatedAt())
        );

        for (Transaction transaction : transactions) {
          logger.info(((Evaluation) transaction).toString());
        }

        long end = System.currentTimeMillis();
        logger.info("API Response time (ms): " + (end - start));

        Thread.sleep(SLEEP);
      } catch (InterruptedException ie) {
        logger.warning(ie.getStackTrace().toString());
      }
    }
  }
}
