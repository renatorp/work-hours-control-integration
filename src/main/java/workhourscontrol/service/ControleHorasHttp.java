package workhourscontrol.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import workhourscontrol.entity.RegistroHora;
import workhourscontrol.exception.ControleHorasException;
import workhourscontrol.strategy.AjusteHorasStrategy;
import workhourscontrol.util.DateUtils;

public abstract class ControleHorasHttp implements ControleHoras {

	private Logger logger = Logger.getLogger(ControleHorasHttp.class);

	private ParametrosControleHorasHttp parametros;
	private boolean loggedIn = false;
	private CloseableHttpClient httpClient;
	private RequestConfig requestGlobalConfig;
	private CookieStore cookieStore;

	public void setParametros(ParametrosControleHorasHttp parametros) {
		this.parametros = parametros;
	}

	private CloseableHttpClient gerarHttpClient() {

		HttpClientBuilder builder = HttpClients.custom();

		builder.setDefaultAuthSchemeRegistry(createAuthSchemeProvider());

		if (parametros.isUsarProxy()) {

			//Definindo rota de acesso ao host
			DefaultProxyRoutePlanner routePlanner = criarRoutePlanner();
			builder.setRoutePlanner(routePlanner);

			//Definindo autentia��o para acesso ao host
			CredentialsProvider credsProvider = criarCredenciais();
			builder.setDefaultCredentialsProvider(credsProvider);

		}

		return  builder.build();

	}

	private Registry<AuthSchemeProvider> createAuthSchemeProvider() {
		Registry<AuthSchemeProvider> r = RegistryBuilder.<AuthSchemeProvider>create()
				.register(AuthSchemes.BASIC, new BasicSchemeFactory())
				.build();

		return r;
	}

	private void login() throws ControleHorasException {

		try {
			httpClient = gerarHttpClient();

			List<NameValuePair> loginParameters = obterParametrosLogin();

			HttpPost post = montarHttpPost(loginParameters, getUrlLogin());

			HttpClientContext localContext = createLocalContext();

			httpClient.execute(post, localContext);

			if (Objects.isNull(getCookieHeader(cookieStore))) {
				throw new ControleHorasException("N�o houve sucesso na autentica��o");
			}

		} catch (IOException e) {
			throw new ControleHorasException("Ocorreu um erro ao efetuar o login.", e);
		} finally {
			fecharConexao();
		}
	}

	private HttpPost montarHttpPost(List<NameValuePair> parameters, String url) throws UnsupportedEncodingException {

		HttpPost post = new HttpPost(url);

		post.setEntity(new UrlEncodedFormEntity(parameters));

		post.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64; rv:12.0) Gecko/20100101 Firefox/21.0");

		post.setConfig(getRequestGlobalConfig());

		return post;

	}

	private HttpGet montarHttpGet(List<NameValuePair> parameters, String url) throws URISyntaxException {

		URIBuilder uriBuilder = new URIBuilder(url);
		uriBuilder.addParameters(parameters);

		HttpGet get = new HttpGet(uriBuilder.build());

		get.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64; rv:12.0) Gecko/20100101 Firefox/21.0");

		get.setConfig(getRequestGlobalConfig());

		return get;

	}

	private RequestConfig getRequestGlobalConfig() {
		if (this.requestGlobalConfig == null) {
			this.requestGlobalConfig = RequestConfig.custom().build();
		}
		return this.requestGlobalConfig;
	}

	private HttpClientContext createLocalContext() {

		//Create local HTTP context
		HttpClientContext localContext = HttpClientContext.create();

		if (this.cookieStore == null) {
			this.cookieStore = new BasicCookieStore();
		}

		//Bind custom cookie store to the local context
		localContext.setCookieStore(cookieStore);

		return localContext;
	}


	protected final void registrarHora(RegistroHora registro) throws ControleHorasException {

		try {
			httpClient = gerarHttpClient();

			HttpPost post = montarHttpPost(montarParametrosRegistroHora(registro), getUrlRegistroHora());

			HttpClientContext localContext = createLocalContext();

			post.addHeader("Cookie", getCookieHeader(cookieStore));

			httpClient.execute(post, localContext);

			registro.setLancado(true);

		} catch (IOException | ParseException e) {
			throw new ControleHorasException("Ocorreu ao registrar hora", e);
		} finally {
			fecharConexao();
		}
	}

	public abstract String getCookieHeader(CookieStore cookieStore);

	protected List<NameValuePair> getParametrosAdicionaisRegistroHora() {
		return Collections.emptyList();
	}

	public abstract String getFormatoParametroData();

	public abstract String getObservacoes();
	public abstract String getNomeParametroHoraFim();
	public abstract String getNomeParametroHoraInicio();
	public abstract String getNomeParametroData();
	public abstract String getNomeParametroIssue();

	public abstract String getUrlRegistroHora();

	@Override
	public void registrarHoras(List<RegistroHora> registros) throws ControleHorasException {
		registros = antesDeRegistrar(registros);
		logarUsuario();
		registrarHorasIndividualmente(registros);
	}

	private List<RegistroHora> antesDeRegistrar(List<RegistroHora> registros) {
		registros = prepararListaRegistros(registros);
		registros = aplicarStrategies(registros);
		return registros;
	}

	private List<RegistroHora> aplicarStrategies(List<RegistroHora> registros) {
		if (CollectionUtils.isNotEmpty(parametros.getAjusteHoratrategies())) {
			for (AjusteHorasStrategy strategy : parametros.getAjusteHoratrategies()) {
				registros = strategy.ajustarRegistros(registros);
			}
		}
		return registros;
	}

	protected List<RegistroHora> prepararListaRegistros(List<RegistroHora> registros) {
		return registros;
	}

	protected void registrarHorasIndividualmente(List<RegistroHora> registros) throws ControleHorasException {
		for (RegistroHora registroHora : registros) {
			registrarHora(registroHora);
		}
	}

	public abstract String getUrlLogin();

	private List<NameValuePair> obterParametrosLogin() {
		List<NameValuePair> loginParameters = new ArrayList<NameValuePair>();

        loginParameters.add(new BasicNameValuePair(getNomeParametroLogin(), parametros.getUser()));
        loginParameters.add(new BasicNameValuePair(getNomeParametroSenha(), parametros.getPassword()));
        loginParameters.addAll(getParametrosAdicionaisLogin());

        return loginParameters;
	}

	protected List<NameValuePair> montarParametrosRegistroHora(RegistroHora registroHora) throws ParseException {
		List<NameValuePair> loginParameters = new ArrayList<NameValuePair>();

		loginParameters.add(new BasicNameValuePair(getNomeParametroData(), DateUtils.formatarData(registroHora.getDia(),registroHora.getMes(),registroHora.getAno(), getFormatoParametroData())));
		loginParameters.add(new BasicNameValuePair(getNomeParametroIssue(), registroHora.getIssue()));
		loginParameters.add(new BasicNameValuePair(getNomeParametroHoraInicio(), registroHora.getHoraInicio()));
		loginParameters.add(new BasicNameValuePair(getNomeParametroHoraFim(), registroHora.getHoraFim()));
		loginParameters.add(new BasicNameValuePair(getObservacoes(), formatarObservacaoParaRequisicao(registroHora)));
		loginParameters.addAll(getParametrosAdicionaisRegistroHora());

        return loginParameters;
	}

	protected String formatarObservacaoParaRequisicao(RegistroHora registroHora) {
		return registroHora.getObservacao();
	}

	protected List<NameValuePair> getParametrosAdicionaisLogin() {
		return Collections.emptyList();
	}

	public abstract String getNomeParametroSenha();

	public abstract String getNomeParametroLogin();

	private void logarUsuario() throws ControleHorasException {
		if (!loggedIn) {
			login();
			this.loggedIn = true;
		}
	}

	private DefaultProxyRoutePlanner criarRoutePlanner() {
		HttpHost proxy = new HttpHost(parametros.getProxyHost(), parametros.getProxyPort());
		DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
		return routePlanner;
	}

	private CredentialsProvider criarCredenciais() {
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
        		 AuthScope.ANY,
        		 new UsernamePasswordCredentials(parametros.getProxyUser(), parametros.getProxyPassword())
        );
		return credsProvider;
	}

	@Override
	public void fecharConexao() {
		try {

			this.loggedIn = false;

			if (httpClient != null) {
				httpClient.close();
				httpClient = null;
			}
		} catch (IOException e) {
			// FIXME: tratar melhor exce��o
			logger.error("Ocorreu ao fechar httpClient", e);
			throw new RuntimeException(e);
		}

	}

	@Override
	public double obterSaldoHoras() throws ControleHorasException {

		try {
			// Usu�rio deve estar logado
			logarUsuario();

			// Vai no servidor para obter p�gina que cont�m o desejado
			String html = obterHtmlServidor();

			// Faz o parse da p�gina para obter o resultado
			return parseHtml(html);

		} catch (Throwable e) {
			throw new ControleHorasException("Ocorreu um erro ao obter saldo de horas", e);
		}

	}

	public abstract double parseHtml(String html);

	private String obterHtmlServidor() throws ControleHorasException {

		try {
			// Monta objeto para fazer o get request
			HttpGet get = montarHttpGet(getParametrosSaldoHoras(), getUrlSaldoHoras());

			// Obt�m local context
			HttpClientContext localContext = createLocalContext();

			// Adiciona Cookie
			get.addHeader("Cookie", getCookieHeader(cookieStore));

			// Faz a requisi��o
			HttpResponse response = gerarHttpClient().execute(get, localContext);

			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

			//Extrai html do response
			StringBuilder htmlResult = new StringBuilder();
			String line = "";
			while ((line = rd.readLine()) != null) {
				htmlResult.append(line);
			}

			return htmlResult.toString();

		} catch (IOException | URISyntaxException e) {
			throw new ControleHorasException("Ocorreu um erro ao obter html do servidor", e);
		} finally {
			fecharConexao();
		}

	}

	public abstract String getUrlSaldoHoras();
	public abstract List<NameValuePair> getParametrosSaldoHoras();


	@SuppressWarnings("unused")
	private void logResponse(HttpResponse response) throws IOException {
		BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

		//Extrai html do response
		StringBuilder htmlResult = new StringBuilder();
		String line = "";
		while ((line = rd.readLine()) != null) {
			htmlResult.append(line);
		}

		logger.info(htmlResult.toString());
	}
}
