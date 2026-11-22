import '@material/web/all.js';
import { styles as typeScaleStyles } from '@material/web/typography/md-typescale-styles.js';

document.adoptedStyleSheets.push(typeScaleStyles.styleSheet);

(() => {
  const $ = (id) => document.getElementById(id);
  const pinInput = $('pin');
  const connectStatus = $('connectStatus');
  const uploadStatus = $('uploadStatus');
  const fileInput = $('files');
  const progress = $('progress');
  const progressLabel = $('progress');
  const deleteDialog = $('deleteDialog');

  let pin = sessionStorage.getItem('fireshare-pin') || '';
  let pendingDelete = null;

  if (pin) pinInput.value = pin;

  function setStatus(element, message, kind = '') {
    element.textContent = message;
    element.dataset.kind = kind;
  }

  function setConnected(value) {
    $('uploadPanel').style.display = value ? 'block' : 'none';
    $('libraryPanel').style.display = value ? 'block' : 'none';
    $('connect').textContent = value ? 'Connected' : 'Connect';
  }

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB';
    return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB';
  }

  function mediaUrl(name) {
    return '/media/' + encodeURIComponent(name) + '?pin=' + encodeURIComponent(pin);
  }

  async function api(path, options) {
    const response = await fetch(path, options);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(data.error || data.message || 'Request failed (' + response.status + ')');
    }
    return data;
  }

  async function connect() {
    pin = String(pinInput.value || '').trim();
    if (!/^\\d{6}$/.test(pin)) {
      setStatus(connectStatus, 'Enter the six-digit PIN displayed on your TV.', 'error');
      return;
    }

    $('connect').disabled = true;
    setStatus(connectStatus, 'Connecting to your Fire TV…');
    try {
      await api('/api/media?pin=' + encodeURIComponent(pin));
      sessionStorage.setItem('fireshare-pin', pin);
      setStatus(connectStatus, 'Connected to your Fire TV.', 'success');
      setConnected(true);
      await loadLibrary();
    } catch (error) {
      sessionStorage.removeItem('fireshare-pin');
      setConnected(false);
      pin = '';
      const message = error.message === 'Incorrect or missing TV access PIN.'
        ? "That PIN wasn't accepted. Check the TV screen and try again."
        : error.message;
      setStatus(connectStatus, message, 'error');
    } finally {
      $('connect').disabled = false;
    }
  }

  async function loadLibrary() {
    const files = await api('/api/media?pin=' + encodeURIComponent(pin));
    $('count').textContent = files.length + (files.length === 1 ? ' ITEM' : ' ITEMS');

    const grid = $('library');
    grid.replaceChildren();

    if (!files.length) {
      const empty = document.createElement('div');
      empty.className = 'empty-state';
      empty.textContent = 'Nothing here yet. Upload a photo or video to get started.';
      grid.append(empty);
      return;
    }

    files.forEach((file) => {
      const card = document.createElement('article');
      card.className = 'media-card';

      const preview = document.createElement('div');
      preview.className = 'media-preview';

      if (file.type === 'video') {
        const video = document.createElement('video');
        video.src = mediaUrl(file.name);
        video.muted = true;
        video.preload = 'metadata';
        preview.append(video);
      } else {
        const image = document.createElement('img');
        image.src = mediaUrl(file.name);
        image.alt = file.name;
        image.loading = 'lazy';
        preview.append(image);
      }

      const meta = document.createElement('div');
      meta.className = 'media-meta';

      const name = document.createElement('div');
      name.className = 'filename';
      name.title = file.name;
      name.textContent = file.name;

      const size = document.createElement('div');
      size.className = 'file-size';
      size.textContent = (file.type === 'video' ? 'VIDEO' : 'PHOTO') + ' · ' + formatSize(file.size);

      const remove = document.createElement('md-outlined-button');
      remove.textContent = 'Remove from TV';
      remove.setAttribute('aria-label', 'Remove ' + file.name + ' from TV');
      remove.addEventListener('click', () => {
        pendingDelete = file;
        $('deleteMessage').textContent = file.name + ' will be permanently removed from your Fire TV.';
        deleteDialog.show();
      });

      meta.append(name, size, remove);
      card.append(preview, meta);
      grid.append(card);
    });
  }

  function updateSelection() {
    const count = fileInput.files.length;
    $('upload').disabled = count === 0;
    $('upload').textContent = count
      ? 'Upload ' + count + (count === 1 ? ' file' : ' files')
      : 'Upload selected files';
  }

  async function uploadFiles() {
    const selected = Array.from(fileInput.files);
    if (!selected.length) return;

    $('upload').disabled = true;
    progress.style.display = 'block';
    progress.value = 0;
    let completed = 0;

    for (const file of selected) {
      setStatus(uploadStatus, 'Uploading ' + file.name + ' (' + (completed + 1) + ' of ' + selected.length + ')…');
      const body = new FormData();
      body.append('file', file, file.name);

      try {
        await api('/upload?pin=' + encodeURIComponent(pin), { method: 'POST', body });
        completed++;
        progress.value = completed / selected.length;
      } catch (error) {
        setStatus(uploadStatus, 'Stopped at ' + file.name + ': ' + error.message, 'error');
        updateSelection();
        return;
      }
    }

    setStatus(uploadStatus, 'Uploaded ' + completed + (completed === 1 ? ' file.' : ' files.'), 'success');
    fileInput.value = '';
    updateSelection();
    await loadLibrary();
  }

  async function deletePendingFile() {
    if (!pendingDelete) return;
    const file = pendingDelete;
    $('confirmDelete').disabled = true;
    try {
      await api('/api/media/' + encodeURIComponent(file.name) + '?pin=' + encodeURIComponent(pin), {
        method: 'DELETE'
      });
      deleteDialog.close();
      pendingDelete = null;
      await loadLibrary();
      setStatus(uploadStatus, 'Removed ' + file.name + ' from your TV.', 'success');
    } catch (error) {
      setStatus(uploadStatus, error.message, 'error');
      deleteDialog.close();
      pendingDelete = null;
    } finally {
      $('confirmDelete').disabled = false;
    }
  }

  $('connect').addEventListener('click', connect);
  $('forget').addEventListener('click', () => {
    sessionStorage.removeItem('fireshare-pin');
    pin = '';
    pinInput.value = '';
    setConnected(false);
    setStatus(connectStatus, 'Saved PIN cleared.');
  });

  $('chooseFiles').addEventListener('click', () => fileInput.click());
  $('upload').addEventListener('click', uploadFiles);
  $('clear').addEventListener('click', () => {
    fileInput.value = '';
    updateSelection();
    setStatus(uploadStatus, '');
    progress.style.display = 'none';
  });
  $('refresh').addEventListener('click', async () => {
    try {
      await loadLibrary();
      setStatus(uploadStatus, 'Library refreshed.', 'success');
    } catch (error) {
      setStatus(uploadStatus, error.message, 'error');
    }
  });
  $('cancelDelete').addEventListener('click', () => {
    pendingDelete = null;
    deleteDialog.close();
  });
  $('confirmDelete').addEventListener('click', deletePendingFile);
  fileInput.addEventListener('change', updateSelection);

  const drop = $('drop');
  ['dragenter', 'dragover'].forEach((type) => drop.addEventListener(type, (event) => {
    event.preventDefault();
    drop.classList.add('drag');
  }));
  ['dragleave', 'drop'].forEach((type) => drop.addEventListener(type, (event) => {
    event.preventDefault();
    drop.classList.remove('drag');
  }));
  drop.addEventListener('drop', (event) => {
    const transfer = new DataTransfer();
    Array.from(event.dataTransfer.files).forEach((file) => transfer.items.add(file));
    fileInput.files = transfer.files;
    updateSelection();
  });

  if (pin) connect();
})();
