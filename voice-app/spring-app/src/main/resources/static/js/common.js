/** fetch() wrapper that adds the Spring Security CSRF header and unwraps JSON errors. */
async function api(url, opts = {}) {
  const token = document.querySelector('meta[name="_csrf"]').content;
  const header = document.querySelector('meta[name="_csrf_header"]').content;
  opts.headers = Object.assign({}, opts.headers, { [header]: token });
  const res = await fetch(url, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || res.statusText);
  return data;
}
