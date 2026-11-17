/** Same-origin JSON/FormData transport shared by the two applications. */
export async function api(path, options = {}) {
  const headers = { 'X-PeerMesh': '1', ...(options.headers || {}) };
  let body = options.body;
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body);
  }
  const response = await fetch(path, { ...options, headers, body, credentials: 'same-origin' });
  const data = await response.json().catch(() => ({ message: 'Phản hồi không hợp lệ.' }));
  if (!response.ok) {
    const error = new Error(data.message || data.detail || 'Yêu cầu thất bại.');
    error.status = response.status;
    throw error;
  }
  return data;
}
