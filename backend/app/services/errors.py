class AIServiceError(Exception):
    """OpenAI call failed (timeout, network, API error, bad response). The app should retry later."""
